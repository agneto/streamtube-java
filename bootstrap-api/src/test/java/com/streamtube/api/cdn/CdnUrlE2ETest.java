package com.streamtube.api.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.VideoProcessingPublisher;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the app with the CDN profile ON and the REAL storage adapter (presigning works offline):
 * read URLs must carry secure_link tokens on the CDN host while upload URLs stay presigned
 * straight to the storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(CdnUrlE2ETest.CdnTestConfig.class)
@TestPropertySource(
    properties = {
      "cdn.enabled=true",
      "cdn.base-url=http://cdn.test:8090/streamtube-videos",
      "cdn.secret=e2e-secret"
    })
class CdnUrlE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private CapturingMailSender mail;

  @Test
  void readUrlsUseTheCdnWhileUploadsStayOnStorage() throws Exception {
    String token = registerConfirmLogin("cdn@test.com");

    // initiate: the upload URL is a presigned STORAGE URL, never the CDN
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of(
                                    "title", "Via CDN",
                                    "sizeBytes", 1000,
                                    "contentType", "video/mp4"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();
    assertThat(init.get("uploadUrl").asText())
        .doesNotContain("cdn.test")
        .contains("X-Amz-Signature");

    // simulate the worker + publish (thumbnail key set so the info URL is exercised too)
    try (Connection c = dataSource.getConnection();
        var st =
            c.prepareStatement(
                "UPDATE videos SET status='READY', thumbnail_key = ? WHERE slug = ?")) {
      st.setString(1, "thumbnails/" + slug + ".jpg");
      st.setString(2, slug);
      st.executeUpdate();
    }
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // stream/download 302s and the thumbnail in the info all point at the CDN with st/e tokens
    String streamUrl =
        mockMvc
            .perform(get("/api/v1/videos/" + slug + "/stream"))
            .andExpect(status().isFound())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    assertThat(streamUrl)
        .startsWith("http://cdn.test:8090/streamtube-videos/videos/" + slug)
        .contains("st=")
        .contains("&e=");

    String downloadUrl =
        mockMvc
            .perform(get("/api/v1/videos/" + slug + "/download"))
            .andExpect(status().isFound())
            .andReturn()
            .getResponse()
            .getHeader("Location");
    assertThat(downloadUrl).startsWith("http://cdn.test:8090/").contains("&dl=");

    JsonNode info =
        readJson(mockMvc.perform(get("/api/v1/videos/" + slug)).andExpect(status().isOk()));
    assertThat(info.get("thumbnailUrl").asText())
        .startsWith("http://cdn.test:8090/streamtube-videos/thumbnails/" + slug + ".jpg?st=");
  }

  // --- helpers (same shape as the other E2E classes) ---

  private String registerConfirmLogin(String email) throws Exception {
    String ip = "10.5." + (Math.abs(email.hashCode()) % 250 + 1) + "." + (email.length() % 250 + 1);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("email", email, "password", "password123")))
                .with(req -> withIp(req, ip)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            get("/api/v1/auth/confirm-email")
                .param("token", mail.confirmationTokens.get(email))
                .with(req -> withIp(req, ip)))
        .andExpect(status().isNoContent());
    JsonNode tokens =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of("email", email, "password", "password123")))
                        .with(req -> withIp(req, ip)))
                .andExpect(status().isOk()));
    return tokens.get("access_token").asText();
  }

  private static org.springframework.mock.web.MockHttpServletRequest withIp(
      org.springframework.mock.web.MockHttpServletRequest request, String ip) {
    request.setRemoteAddr(ip);
    return request;
  }

  private JsonNode readJson(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  static class CapturingMailSender implements MailSender {
    final Map<String, String> confirmationTokens = new ConcurrentHashMap<>();

    @Override
    public void sendConfirmationEmail(String to, String rawToken) {
      confirmationTokens.put(to, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken) {}
  }

  static class FakePublisher implements VideoProcessingPublisher {
    @Override
    public void publish(UUID videoId) {}
  }

  /** Mail/queue faked; the STORAGE stays real — presigning needs no connectivity. */
  @TestConfiguration
  static class CdnTestConfig {
    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
      return new CapturingMailSender();
    }

    @Bean
    @Primary
    FakePublisher fakePublisher() {
      return new FakePublisher();
    }
  }
}
