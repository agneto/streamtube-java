package com.streamtube.api.channel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.api.testsupport.FakeStorage;
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

/**
 * Full HTTP cycle for channel editing, the owner panel listing and the public channel page (real
 * Postgres via Testcontainers; storage/queue faked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(ChannelE2ETest.ChannelTestConfig.class)
class ChannelE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private CapturingMailSender mail;

  @Test
  void ownerUpdatesChannelInfo() throws Exception {
    String token = registerConfirmLogin("channel-owner@test.com");

    mockMvc
        .perform(
            jsonPatch("/api/v1/channels/me", token, Map.of("description", "Minha nova bio")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Minha nova bio"))
        .andExpect(jsonPath("$.nickname").exists());

    // partial update: name/nickname change, untouched description stays
    mockMvc
        .perform(
            jsonPatch(
                "/api/v1/channels/me",
                token,
                Map.of("name", "Canal Renomeado", "nickname", "canal-renomeado")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Canal Renomeado"))
        .andExpect(jsonPath("$.nickname").value("canal-renomeado"))
        .andExpect(jsonPath("$.description").value("Minha nova bio"));
  }

  @Test
  void blankDescriptionClearsItAndNullLeavesItUntouched() throws Exception {
    String token = registerConfirmLogin("channel-clear@test.com");

    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", token, Map.of("description", "bio")))
        .andExpect(status().isOk());

    // {} (all fields absent) leaves everything untouched
    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", token, Map.of()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("bio"));

    // blank clears
    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", token, Map.of("description", " ")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  void nicknameAlreadyTakenIsConflict() throws Exception {
    String first = registerConfirmLogin("nick-first@test.com");
    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", first, Map.of("nickname", "nick-disputado")))
        .andExpect(status().isOk());

    String second = registerConfirmLogin("nick-second@test.com");
    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", second, Map.of("nickname", "nick-disputado")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NICKNAME_ALREADY_TAKEN"));
  }

  @Test
  void invalidNicknameIsRejected() throws Exception {
    String token = registerConfirmLogin("nick-invalid@test.com");
    mockMvc
        .perform(jsonPatch("/api/v1/channels/me", token, Map.of("nickname", "tem espaço")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
  }

  @Test
  void updateRequiresAuth() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/channels/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("description", "x"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void panelListsEverythingAndPublicPageListsOnlyPublishedPublic() throws Exception {
    String token = registerConfirmLogin("listing-owner@test.com");
    String nickname =
        readJson(mockMvc.perform(jsonPatch("/api/v1/channels/me", token, Map.of())))
            .get("nickname")
            .asText();

    // A: published PUBLIC; B: published UNLISTED; C: draft (never published)
    String slugA = initiateVideo(token, "Video A");
    String slugB = initiateVideo(token, "Video B");
    initiateVideo(token, "Video C");
    publish(token, slugA);
    mockMvc
        .perform(
            jsonPatch(
                "/api/v1/videos/" + videoId(slugB), token, Map.of("visibility", "UNLISTED")))
        .andExpect(status().isOk());
    publish(token, slugB);

    // public page: only A
    mockMvc
        .perform(get("/api/v1/channels/" + nickname + "/videos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].slug").value(slugA));

    // public header
    mockMvc
        .perform(get("/api/v1/channels/" + nickname))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value(nickname))
        .andExpect(jsonPath("$.userId").doesNotExist());

    // owner panel: all three, newest first, page envelope honors size
    mockMvc
        .perform(get("/api/v1/channels/me/videos").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.items[0].title").value("Video C"));
    mockMvc
        .perform(
            get("/api/v1/channels/me/videos?page=1&size=2")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));

    // the panel is not public
    mockMvc.perform(get("/api/v1/channels/me/videos")).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownNicknameIsNotFound() throws Exception {
    mockMvc.perform(get("/api/v1/channels/nao-existe")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/channels/nao-existe/videos")).andExpect(status().isNotFound());
  }

  // --- helpers ---

  private String initiateVideo(String token, String title) throws Exception {
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
                                    "title", title,
                                    "sizeBytes", 1000,
                                    "contentType", "video/mp4"))))
                .andExpect(status().isCreated()));
    return init.get("slug").asText();
  }

  /** Simulates the worker finishing, then publishes through the API. */
  private void publish(String token, String slug) throws Exception {
    try (Connection c = dataSource.getConnection();
        var st = c.prepareStatement("UPDATE videos SET status='READY' WHERE slug = ?")) {
      st.setString(1, slug);
      st.executeUpdate();
    }
    mockMvc
        .perform(
            post("/api/v1/videos/" + videoId(slug) + "/publish")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  private UUID videoId(String slug) throws Exception {
    try (Connection c = dataSource.getConnection();
        var st = c.prepareStatement("SELECT id FROM videos WHERE slug = ?")) {
      st.setString(1, slug);
      var rs = st.executeQuery();
      rs.next();
      return rs.getObject(1, UUID.class);
    }
  }

  private String registerConfirmLogin(String email) throws Exception {
    // Unique client IP per test user so the per-IP auth rate limit never trips across tests.
    String ip = "10.8." + (Math.abs(email.hashCode()) % 250 + 1) + "." + (email.length() % 250 + 1);
    mockMvc
        .perform(
            jsonPost("/api/v1/auth/register", Map.of("email", email, "password", "password123"))
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
                    jsonPost("/api/v1/auth/login", Map.of("email", email, "password", "password123"))
                        .with(req -> withIp(req, ip)))
                .andExpect(status().isOk()));
    return tokens.get("access_token").asText();
  }

  private static org.springframework.mock.web.MockHttpServletRequest withIp(
      org.springframework.mock.web.MockHttpServletRequest request, String ip) {
    request.setRemoteAddr(ip);
    return request;
  }

  private MockHttpServletRequestBuilder jsonPatch(String path, String token, Map<String, ?> body)
      throws Exception {
    return patch(path)
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
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


  static class FakePublisher implements VideoProcessingPublisher {
    @Override
    public void publish(UUID videoId) {}
  }

  @TestConfiguration
  static class ChannelTestConfig {
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
