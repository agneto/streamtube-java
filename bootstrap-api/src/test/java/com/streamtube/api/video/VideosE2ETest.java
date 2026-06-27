package com.streamtube.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.StoragePort;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full HTTP cycle for the video endpoints (storage + queue faked; real Postgres via Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(VideosE2ETest.VideoTestConfig.class)
class VideosE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private CapturingMailSender mail;
  @Autowired private FakePublisher publisher;

  @Test
  void initiateCompleteAndStreamFlow() throws Exception {
    String token = registerConfirmLogin("videoflow@test.com");

    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "My Video"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();
    assertThat(init.get("uploadUrl").asText()).contains("upload");

    // complete-upload (fake storage reports the object exists) -> 204, queued, job published
    mockMvc
        .perform(post("/videos/" + id + "/complete-upload").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    assertThat(publisher.lastVideoId).isEqualTo(UUID.fromString(id));

    // public info
    mockMvc
        .perform(get("/videos/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(slug))
        .andExpect(jsonPath("$.status").value("QUEUED"));

    // not ready -> stream 422
    mockMvc.perform(get("/videos/" + slug + "/stream")).andExpect(status().isUnprocessableEntity());

    // simulate worker finishing
    markReady(slug);

    mockMvc
        .perform(get("/videos/" + slug + "/stream"))
        .andExpect(status().isFound())
        .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).contains("stream"));
    mockMvc.perform(get("/videos/" + slug + "/download")).andExpect(status().isFound());
  }

  @Test
  void initiateRequiresAuth() throws Exception {
    mockMvc
        .perform(
            post("/videos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "X"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownSlugIsNotFound() throws Exception {
    mockMvc.perform(get("/videos/does-not-ex")).andExpect(status().isNotFound());
  }

  @Test
  void completeUploadByNonOwnerIsForbidden() throws Exception {
    String owner = registerConfirmLogin("owner-v@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/videos")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Owned"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();

    String other = registerConfirmLogin("other-v@test.com");
    mockMvc
        .perform(post("/videos/" + id + "/complete-upload").header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
  }

  // --- helpers ---

  private void markReady(String slug) throws Exception {
    try (Connection c = dataSource.getConnection();
        var st = c.prepareStatement("UPDATE videos SET status='READY' WHERE slug = ?")) {
      st.setString(1, slug);
      st.executeUpdate();
    }
  }

  private String registerConfirmLogin(String email) throws Exception {
    mockMvc
        .perform(jsonPost("/auth/register", Map.of("email", email, "password", "password123")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/auth/confirm-email").param("token", mail.confirmationTokens.get(email)))
        .andExpect(status().isNoContent());
    JsonNode tokens =
        readJson(
            mockMvc
                .perform(jsonPost("/auth/login", Map.of("email", email, "password", "password123")))
                .andExpect(status().isOk()));
    return tokens.get("access_token").asText();
  }

  private MockHttpServletRequestBuilder jsonPost(String path, Map<String, String> body)
      throws Exception {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
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

  static class FakeStorage implements StoragePort {
    @Override
    public String presignUpload(String key) {
      return "http://localhost:9000/" + key + "?upload&sig=x";
    }

    @Override
    public String presignStream(String key) {
      return "http://localhost:9000/" + key + "?stream&sig=x";
    }

    @Override
    public String presignDownload(String key, String filename) {
      return "http://localhost:9000/" + key + "?download&response-content-disposition=attachment";
    }

    @Override
    public String presignInternal(String key) {
      return "http://minio:9000/" + key + "?internal&sig=x";
    }

    @Override
    public void putObject(String key, byte[] body, String contentType) {}

    @Override
    public boolean objectExists(String key) {
      return true;
    }
  }

  static class FakePublisher implements VideoProcessingPublisher {
    volatile UUID lastVideoId;

    @Override
    public void publish(UUID videoId) {
      this.lastVideoId = videoId;
    }
  }

  @TestConfiguration
  static class VideoTestConfig {
    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
      return new CapturingMailSender();
    }

    @Bean
    @Primary
    FakeStorage fakeStorage() {
      return new FakeStorage();
    }

    @Bean
    @Primary
    FakePublisher fakePublisher() {
      return new FakePublisher();
    }
  }
}
