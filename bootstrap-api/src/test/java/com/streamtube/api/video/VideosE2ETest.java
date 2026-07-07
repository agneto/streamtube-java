package com.streamtube.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("My Video"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();
    assertThat(init.get("uploadUrl").asText()).contains("upload");

    // complete-upload (fake storage reports the object exists) -> 204, queued, job published
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/complete-upload").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    assertThat(publisher.lastVideoId).isEqualTo(UUID.fromString(id));

    // drafts are owner-only: anonymous info is 404, the owner sees the processing status
    mockMvc.perform(get("/api/v1/videos/" + slug)).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/videos/" + slug).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(slug))
        .andExpect(jsonPath("$.status").value("QUEUED"))
        .andExpect(jsonPath("$.publishedAt").doesNotExist());

    // not ready -> stream 422 (for the owner; anonymous would get the draft 404)
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/stream").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    // publish before READY -> 422
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    // simulate worker finishing, then publish
    markReady(slug);
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publishedAt").exists());

    // once published, the video is open to anonymous viewers
    mockMvc
        .perform(get("/api/v1/videos/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"));
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/stream"))
        .andExpect(status().isFound())
        .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).contains("stream"));
    mockMvc.perform(get("/api/v1/videos/" + slug + "/download")).andExpect(status().isFound());
  }

  @Test
  void initiateRequiresAuth() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/videos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "X"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownSlugIsNotFound() throws Exception {
    mockMvc.perform(get("/api/v1/videos/does-not-ex")).andExpect(status().isNotFound());
  }

  @Test
  void ownerCanRenameVideo() throws Exception {
    String token = registerConfirmLogin("rename-owner@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Título antigo"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Título novo"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Título novo"));
  }

  @Test
  void renameByNonOwnerIsForbidden() throws Exception {
    String owner = registerConfirmLogin("rename-owner2@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Dono"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();

    String other = registerConfirmLogin("rename-other@test.com");
    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "Invasor"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void renameRequiresAuth() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/videos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("title", "x"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void completeUploadByNonOwnerIsForbidden() throws Exception {
    String owner = registerConfirmLogin("owner-v@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Owned"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();

    String other = registerConfirmLogin("other-v@test.com");
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/complete-upload").header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());
  }

  @Test
  void initiateRejectsNonVideoContentType() throws Exception {
    String token = registerConfirmLogin("wrong-type@test.com");
    mockMvc
        .perform(
            post("/api/v1/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("title", "Doc", "sizeBytes", 1000, "contentType", "application/pdf"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_VIDEO_TYPE"));
  }

  @Test
  void initiateRejectsOversizedUpload() throws Exception {
    String token = registerConfirmLogin("too-big@test.com");
    mockMvc
        .perform(
            post("/api/v1/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "title", "Huge",
                            "sizeBytes", Long.MAX_VALUE,
                            "contentType", "video/mp4"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_SIZE"));
  }

  @Test
  void draftIsHiddenFromOtherAuthenticatedUsers() throws Exception {
    String owner = registerConfirmLogin("draft-owner@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Rascunho"))))
                .andExpect(status().isCreated()));
    String slug = init.get("slug").asText();

    // 404 (not 403): a draft must not leak its existence
    String other = registerConfirmLogin("draft-other@test.com");
    mockMvc
        .perform(get("/api/v1/videos/" + slug).header("Authorization", "Bearer " + other))
        .andExpect(status().isNotFound());
  }

  @Test
  void unlistedVideoIsReachableBySlugAfterPublish() throws Exception {
    String token = registerConfirmLogin("unlisted@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Não listado"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();

    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("visibility", "UNLISTED"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("UNLISTED"));

    markReady(slug);
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // link-only by design: anyone with the slug can watch
    mockMvc.perform(get("/api/v1/videos/" + slug)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/videos/" + slug + "/stream")).andExpect(status().isFound());
  }

  @Test
  void ownerEditsDescriptionCategoryAndVisibility() throws Exception {
    String token = registerConfirmLogin("edit-all@test.com");
    JsonNode categories =
        readJson(mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()));
    assertThat(categories.size()).isEqualTo(8); // seeded by V6
    String categoryId = categories.get(0).get("id").asText();

    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Completo"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "description", "Uma descrição",
                            "categoryId", categoryId,
                            "visibility", "UNLISTED"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Completo")) // untouched by the partial update
        .andExpect(jsonPath("$.description").value("Uma descrição"))
        .andExpect(jsonPath("$.categoryId").value(categoryId))
        .andExpect(jsonPath("$.visibility").value("UNLISTED"));

    // unknown category -> 400
    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("categoryId", UUID.randomUUID().toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

    // invalid visibility value -> 400
    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("visibility", "PRIVATE"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void customThumbnailFlow() throws Exception {
    String token = registerConfirmLogin("thumb@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Com thumb"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();

    // non-image content type -> 400
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/thumbnail")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("sizeBytes", 1024, "contentType", "video/mp4"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_THUMBNAIL_TYPE"));

    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/thumbnail")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("sizeBytes", 1024, "contentType", "image/png"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uploadUrl").exists());

    // complete before READY -> 422 (fake storage says the object exists)
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/thumbnail/complete")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isUnprocessableEntity());

    markReady(slug);
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/thumbnail/complete")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.thumbnailUrl", org.hamcrest.Matchers.containsString(slug + "-custom")));
  }

  @Test
  void categoriesArePublic() throws Exception {
    mockMvc
        .perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].slug").exists());
  }

  // --- helpers ---

  private Map<String, Object> initiateBody(String title) {
    return Map.of("title", title, "sizeBytes", 1000, "contentType", "video/mp4");
  }

  private void markReady(String slug) throws Exception {
    try (Connection c = dataSource.getConnection();
        var st = c.prepareStatement("UPDATE videos SET status='READY' WHERE slug = ?")) {
      st.setString(1, slug);
      st.executeUpdate();
    }
  }

  private String registerConfirmLogin(String email) throws Exception {
    // Unique client IP per test user so the per-IP auth rate limit never trips across tests.
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
