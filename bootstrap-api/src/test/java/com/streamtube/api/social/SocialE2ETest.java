package com.streamtube.api.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.StoragePort;
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
 * Full HTTP cycle for Phase 06: reactions with exact counters, comments with single-level replies
 * and channel subscriptions (real Postgres via Testcontainers; storage/queue faked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(SocialE2ETest.SocialTestConfig.class)
class SocialE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataSource dataSource;
  @Autowired private CapturingMailSender mail;

  @Test
  void reactionLifecycleKeepsCountersExact() throws Exception {
    String owner = registerConfirmLogin("react-owner@test.com");
    String viewer = registerConfirmLogin("react-viewer@test.com");
    Video video = publishedVideo(owner, "Com reações");

    // set LIKE — twice, to prove idempotency (no double count)
    react(viewer, "/api/v1/videos/" + video.id + "/reaction", "LIKE");
    react(viewer, "/api/v1/videos/" + video.id + "/reaction", "LIKE");
    JsonNode info = info(video.slug, viewer);
    assertThat(info.get("likes").asLong()).isEqualTo(1);
    assertThat(info.get("dislikes").asLong()).isZero();
    assertThat(info.get("myReaction").asText()).isEqualTo("LIKE");

    // switch to DISLIKE adjusts BOTH counters
    react(viewer, "/api/v1/videos/" + video.id + "/reaction", "DISLIKE");
    info = info(video.slug, viewer);
    assertThat(info.get("likes").asLong()).isZero();
    assertThat(info.get("dislikes").asLong()).isEqualTo(1);
    assertThat(info.get("myReaction").asText()).isEqualTo("DISLIKE");

    // remove — twice (idempotent), and anonymous info has null myReaction
    mockMvc
        .perform(delete("/api/v1/videos/" + video.id + "/reaction").header("Authorization", "Bearer " + viewer))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(delete("/api/v1/videos/" + video.id + "/reaction").header("Authorization", "Bearer " + viewer))
        .andExpect(status().isNoContent());
    info = info(video.slug, null);
    assertThat(info.get("likes").asLong()).isZero();
    assertThat(info.get("dislikes").asLong()).isZero();
    assertThat(info.get("myReaction").isNull()).isTrue();
  }

  @Test
  void reactionsRequireAuthAndPublishedVideo() throws Exception {
    String owner = registerConfirmLogin("react-draft@test.com");
    Video draft = readyDraft(owner, "Rascunho");

    // anonymous → 401
    mockMvc
        .perform(
            put("/api/v1/videos/" + draft.id + "/reaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
        .andExpect(status().isUnauthorized());

    // owner on own draft → 409 (exists for them, just not published)
    mockMvc
        .perform(
            put("/api/v1/videos/" + draft.id + "/reaction")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VIDEO_NOT_PUBLISHED"));

    // other user on the draft → 404 (existence never leaks)
    String other = registerConfirmLogin("react-other@test.com");
    mockMvc
        .perform(
            put("/api/v1/videos/" + draft.id + "/reaction")
                .header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void commentThreadFlowKeepsCountersExact() throws Exception {
    String owner = registerConfirmLogin("comment-owner@test.com");
    String viewer = registerConfirmLogin("comment-viewer@test.com");
    Video video = publishedVideo(owner, "Com comentários");

    // top-level comment by the viewer, reply by the owner
    JsonNode comment = comment(viewer, video.id, "Primeiro!", null);
    String commentId = comment.get("id").asText();
    assertThat(comment.get("author").get("nickname").asText()).isNotBlank();
    comment(owner, video.id, "Obrigado!", commentId);

    // reply to a reply → 400
    String replyId =
        readJson(
                mockMvc
                    .perform(get("/api/v1/comments/" + commentId + "/replies"))
                    .andExpect(status().isOk()))
            .get("items")
            .get(0)
            .get("id")
            .asText();
    mockMvc
        .perform(
            post("/api/v1/videos/" + video.id + "/comments")
                .header("Authorization", "Bearer " + viewer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("content", "resposta da resposta", "parentId", replyId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"));

    // counters: video counts top-level + replies; the comment counts its replies
    assertThat(info(video.slug, null).get("commentsCount").asLong()).isEqualTo(2);
    JsonNode topLevel =
        readJson(
            mockMvc
                .perform(get("/api/v1/videos/" + video.slug + "/comments"))
                .andExpect(status().isOk()));
    assertThat(topLevel.get("totalItems").asLong()).isEqualTo(1); // replies are not top-level
    assertThat(topLevel.get("items").get(0).get("repliesCount").asLong()).isEqualTo(1);

    // only the author deletes (403 for others); deleting the root removes the reply too
    mockMvc
        .perform(delete("/api/v1/comments/" + commentId).header("Authorization", "Bearer " + owner))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/comments/" + commentId).header("Authorization", "Bearer " + viewer))
        .andExpect(status().isNoContent());
    assertThat(info(video.slug, null).get("commentsCount").asLong()).isZero();
    mockMvc.perform(get("/api/v1/comments/" + replyId + "/replies")).andExpect(status().isNotFound());
  }

  @Test
  void commentReactionsWork() throws Exception {
    String owner = registerConfirmLogin("creact-owner@test.com");
    String viewer = registerConfirmLogin("creact-viewer@test.com");
    Video video = publishedVideo(owner, "Reação em comentário");
    String commentId = comment(viewer, video.id, "Curtam!", null).get("id").asText();

    react(owner, "/api/v1/comments/" + commentId + "/reaction", "LIKE");
    react(viewer, "/api/v1/comments/" + commentId + "/reaction", "DISLIKE");

    JsonNode items =
        readJson(
                mockMvc
                    .perform(get("/api/v1/videos/" + video.slug + "/comments"))
                    .andExpect(status().isOk()))
            .get("items");
    assertThat(items.get(0).get("likes").asLong()).isEqualTo(1);
    assertThat(items.get(0).get("dislikes").asLong()).isEqualTo(1);
  }

  @Test
  void subscriptionFlowKeepsCounterExactAndFeedsVideos() throws Exception {
    String owner = registerConfirmLogin("sub-owner@test.com");
    String follower = registerConfirmLogin("sub-follower@test.com");
    String nickname = "canal-seguido";
    mockMvc
        .perform(
            patch("/api/v1/channels/me")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("nickname", nickname))))
        .andExpect(status().isOk());
    Video published = publishedVideo(owner, "No feed");
    readyDraft(owner, "Fora do feed"); // draft never reaches the feed

    // subscribe — twice, to prove the counter cannot double-count
    subscribe(follower, nickname);
    subscribe(follower, nickname);
    mockMvc
        .perform(get("/api/v1/channels/" + nickname).header("Authorization", "Bearer " + follower))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscribersCount").value(1))
        .andExpect(jsonPath("$.subscribed").value(true));
    mockMvc
        .perform(get("/api/v1/channels/" + nickname))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscribed").value(false)); // anonymous

    // self-subscribe → 400
    mockMvc
        .perform(
            put("/api/v1/channels/" + nickname + "/subscription")
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_SUBSCRIPTION"));

    // subscribed-channels area: channel listing + video feed (published PUBLIC only)
    mockMvc
        .perform(get("/api/v1/subscriptions").header("Authorization", "Bearer " + follower))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(1))
        .andExpect(jsonPath("$.items[0].nickname").value(nickname))
        .andExpect(jsonPath("$.items[0].subscribersCount").value(1));
    JsonNode feed =
        readJson(
            mockMvc
                .perform(
                    get("/api/v1/subscriptions/videos").header("Authorization", "Bearer " + follower))
                .andExpect(status().isOk()));
    assertThat(feed.get("totalItems").asLong()).isEqualTo(1);
    assertThat(feed.get("items").get(0).get("slug").asText()).isEqualTo(published.slug);

    // unsubscribe — twice (idempotent), counter back to zero
    unsubscribe(follower, nickname);
    unsubscribe(follower, nickname);
    mockMvc
        .perform(get("/api/v1/channels/" + nickname))
        .andExpect(jsonPath("$.subscribersCount").value(0));

    // the area is not public
    mockMvc.perform(get("/api/v1/subscriptions")).andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private record Video(String id, String slug) {}

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
        .perform(post("/api/v1/videos/" + video.id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    return video;
  }

  private void react(String token, String path, String type) throws Exception {
    mockMvc
        .perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"}"))
        .andExpect(status().isNoContent());
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

  private void unsubscribe(String token, String nickname) throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/channels/" + nickname + "/subscription")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private JsonNode info(String slug, String token) throws Exception {
    MockHttpServletRequestBuilder request = get("/api/v1/videos/" + slug);
    if (token != null) {
      request = request.header("Authorization", "Bearer " + token);
    }
    return readJson(mockMvc.perform(request).andExpect(status().isOk()));
  }

  private String registerConfirmLogin(String email) throws Exception {
    // Unique client IP per test user so the per-IP auth rate limit never trips across tests.
    String ip = "10.7." + (Math.abs(email.hashCode()) % 250 + 1) + "." + (email.length() % 250 + 1);
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
    public String presignUpload(String key, long contentLength, String contentType) {
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
    @Override
    public void publish(UUID videoId) {}
  }

  @TestConfiguration
  static class SocialTestConfig {
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
