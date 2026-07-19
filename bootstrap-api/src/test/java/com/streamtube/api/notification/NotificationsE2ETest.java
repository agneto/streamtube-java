package com.streamtube.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.api.testsupport.FakeStorage;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.VideoProcessingPublisher;
import java.sql.Connection;
import java.util.HashMap;
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
 * Full HTTP cycle for Phase 13: the four social triggers drop feed rows for the affected user, the
 * feed is recipient-scoped (no IDOR on mark-read), and Phase 11's video deletion cascades a video's
 * notifications away (real Postgres via Testcontainers; storage/queue faked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(NotificationsE2ETest.NotificationTestConfig.class)
class NotificationsE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private CapturingMailSender mail;

  @Test
  void socialTriggersFeedTheAffectedUserAndVideoDeletionCascades() throws Exception {
    String creator = registerConfirmLogin("notif-creator@test.com");
    String fan = registerConfirmLogin("notif-fan@test.com");
    setNickname(creator, "creator");
    setNickname(fan, "fan");

    // (1) fan subscribes to creator → creator gets NEW_SUBSCRIBER
    subscribe(fan, "creator");
    // (2) creator publishes a PUBLIC video → fan (a subscriber) gets NEW_VIDEO
    Video video = publishedVideo(creator, "Novidade");
    // (3) fan comments on it → creator gets VIDEO_COMMENT
    JsonNode topComment = comment(fan, video.id, "primeiro!", null);
    // (4) creator replies → fan gets COMMENT_REPLY
    comment(creator, video.id, "obrigado!", topComment.get("id").asText());

    // creator's feed: VIDEO_COMMENT (newest) then NEW_SUBSCRIBER
    JsonNode creatorFeed = feed(creator);
    assertThat(creatorFeed.get("totalItems").asLong()).isEqualTo(2);
    assertThat(creatorFeed.get("items").get(0).get("type").asText()).isEqualTo("VIDEO_COMMENT");
    assertThat(creatorFeed.get("items").get(0).get("actor").get("nickname").asText())
        .isEqualTo("fan");
    assertThat(creatorFeed.get("items").get(0).get("video").get("slug").asText())
        .isEqualTo(video.slug);
    assertThat(creatorFeed.get("items").get(0).get("comment").get("content").asText())
        .isEqualTo("primeiro!");
    assertThat(creatorFeed.get("items").get(1).get("type").asText()).isEqualTo("NEW_SUBSCRIBER");
    assertThat(unreadCount(creator)).isEqualTo(2);

    // fan's feed: COMMENT_REPLY (newest) then NEW_VIDEO
    JsonNode fanFeed = feed(fan);
    assertThat(fanFeed.get("totalItems").asLong()).isEqualTo(2);
    assertThat(fanFeed.get("items").get(0).get("type").asText()).isEqualTo("COMMENT_REPLY");
    assertThat(fanFeed.get("items").get(1).get("type").asText()).isEqualTo("NEW_VIDEO");
    assertThat(fanFeed.get("items").get(1).get("video").get("slug").asText()).isEqualTo(video.slug);
    assertThat(unreadCount(fan)).isEqualTo(2);

    // IDOR: fan cannot flip creator's NEW_SUBSCRIBER (204, but a no-op)
    String creatorSubNotifId = creatorFeed.get("items").get(1).get("id").asText();
    markRead(fan, creatorSubNotifId);
    assertThat(unreadCount(creator)).isEqualTo(2);

    // creator marks that one read → 1 left; then read-all → 0
    markRead(creator, creatorSubNotifId);
    assertThat(unreadCount(creator)).isEqualTo(1);
    markAllRead(creator);
    assertThat(unreadCount(creator)).isZero();

    // (5) deleting the video cascades its notifications away (Phase 11 ethos): fan's NEW_VIDEO and
    // COMMENT_REPLY (both reference the video) vanish; creator's NEW_SUBSCRIBER (no video) survives.
    mockMvc
        .perform(delete("/api/v1/videos/" + video.id).header("Authorization", "Bearer " + creator))
        .andExpect(status().isNoContent());

    assertThat(feed(fan).get("totalItems").asLong()).isZero();
    assertThat(unreadCount(fan)).isZero();
    JsonNode creatorAfter = feed(creator);
    assertThat(creatorAfter.get("totalItems").asLong()).isEqualTo(1);
    assertThat(creatorAfter.get("items").get(0).get("type").asText()).isEqualTo("NEW_SUBSCRIBER");
  }

  @Test
  void feedRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
  }

  @Test
  void reSubscribeDoesNotDuplicateNotification() throws Exception {
    String creator = registerConfirmLogin("notif-resub-creator@test.com");
    String fan = registerConfirmLogin("notif-resub-fan@test.com");
    setNickname(creator, "resub-creator");

    subscribe(fan, "resub-creator");
    subscribe(fan, "resub-creator"); // idempotent — must not add a second NEW_SUBSCRIBER

    assertThat(unreadCount(creator)).isEqualTo(1);
  }

  // --- helpers ---

  private record Video(String id, String slug) {}

  private JsonNode feed(String token) throws Exception {
    return readJson(
        mockMvc
            .perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()));
  }

  private long unreadCount(String token) throws Exception {
    return readJson(
            mockMvc
                .perform(
                    get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()))
        .get("count")
        .asLong();
  }

  private void markRead(String token, String id) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private void markAllRead(String token) throws Exception {
    mockMvc
        .perform(post("/api/v1/notifications/read-all").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private void setNickname(String token, String nickname) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/channels/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nickname", nickname))))
        .andExpect(status().isOk());
  }

  private Video readyDraft(String token, String title) throws Exception {
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
    String slug = init.get("slug").asText();
    try (Connection c = dataSource.getConnection();
        var st = c.prepareStatement("UPDATE videos SET status='READY' WHERE slug = ?")) {
      st.setString(1, slug);
      st.executeUpdate();
    }
    return new Video(init.get("id").asText(), slug);
  }

  private Video publishedVideo(String token, String title) throws Exception {
    Video video = readyDraft(token, title);
    mockMvc
        .perform(
            post("/api/v1/videos/" + video.id + "/publish")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    return video;
  }

  private JsonNode comment(String token, String videoId, String content, String parentId)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("content", content);
    if (parentId != null) {
      body.put("parentId", parentId);
    }
    return readJson(
        mockMvc
            .perform(
                post("/api/v1/videos/" + videoId + "/comments")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated()));
  }

  private void subscribe(String token, String nickname) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/channels/" + nickname + "/subscription")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private String registerConfirmLogin(String email) throws Exception {
    String ip = "10.9." + (Math.abs(email.hashCode()) % 250 + 1) + "." + (email.length() % 250 + 1);
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
                    jsonPost(
                            "/api/v1/auth/login",
                            Map.of("email", email, "password", "password123"))
                        .with(req -> withIp(req, ip)))
                .andExpect(status().isOk()));
    return tokens.get("access_token").asText();
  }

  private static org.springframework.mock.web.MockHttpServletRequest withIp(
      org.springframework.mock.web.MockHttpServletRequest request, String ip) {
    request.setRemoteAddr(ip);
    return request;
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
  static class NotificationTestConfig {
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
