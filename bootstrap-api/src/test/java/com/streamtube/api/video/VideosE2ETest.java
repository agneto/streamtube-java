package com.streamtube.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
  @Autowired private FakeStorage fakeStorage;

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
  void streamCountsViewsOnlyForPublishedVideos() throws Exception {
    String token = registerConfirmLogin("views@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Com views"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();
    markReady(slug);

    // the owner previewing the draft plays it, but is not audience
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/stream").header("Authorization", "Bearer " + token))
        .andExpect(status().isFound());
    mockMvc
        .perform(get("/api/v1/videos/" + slug).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(0));

    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(0));

    // each published stream counts (no dedup by design: reloads count again)
    mockMvc.perform(get("/api/v1/videos/" + slug + "/stream")).andExpect(status().isFound());
    mockMvc.perform(get("/api/v1/videos/" + slug + "/stream")).andExpect(status().isFound());
    // downloads are not views
    mockMvc.perform(get("/api/v1/videos/" + slug + "/download")).andExpect(status().isFound());
    mockMvc
        .perform(get("/api/v1/videos/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.views").value(2));
  }

  @Test
  void relatedVideosAreSameCategoryPublishedPublicOnly() throws Exception {
    String token = registerConfirmLogin("related@test.com");
    JsonNode categories =
        readJson(mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()));
    String categoryA = categories.get(0).get("id").asText();
    String categoryB = categories.get(1).get("id").asText();

    String baseSlug = createCategorizedVideo(token, "Base", categoryA, null, true);
    String sameCategory = createCategorizedVideo(token, "Sugestão", categoryA, null, true);
    createCategorizedVideo(token, "Unlisted", categoryA, "UNLISTED", true); // never suggested
    String draftSlug = createCategorizedVideo(token, "Draft", categoryA, null, false);
    createCategorizedVideo(token, "Outra categoria", categoryB, null, true);

    JsonNode related =
        readJson(
            mockMvc
                .perform(get("/api/v1/videos/" + baseSlug + "/related"))
                .andExpect(status().isOk()));
    assertThat(related.findValuesAsText("slug")).containsExactly(sameCategory);
    assertThat(related.get(0).get("views").asLong()).isZero();

    // the base video itself follows the read rule: draft -> 404 for non-owners
    mockMvc.perform(get("/api/v1/videos/" + draftSlug + "/related")).andExpect(status().isNotFound());

    // limit is validated/clamped, never a 500
    mockMvc
        .perform(get("/api/v1/videos/" + baseSlug + "/related").param("limit", "50"))
        .andExpect(status().isOk());
  }

  @Test
  void multipartUploadWithRetryAndResume() throws Exception {
    String token = registerConfirmLogin("multipart@test.com");
    long size = 20_000_000L; // 3 parts at the default 8 MiB
    long partSize = 8L * 1024 * 1024;
    long lastPart = size - 2 * partSize;

    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos/multipart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of(
                                    "title", "Grande",
                                    "sizeBytes", size,
                                    "contentType", "video/mp4"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    assertThat(init.get("partSizeBytes").asLong()).isEqualTo(partSize);
    assertThat(init.get("totalParts").asInt()).isEqualTo(3);
    String uploadId = fakeStorage.onlyOpenUploadId();

    // part URLs sign the exact length of each part (last = remainder), re-issuable at will
    JsonNode urls =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos/" + id + "/parts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partNumbers\":[1,2,3]}"))
                .andExpect(status().isOk()));
    assertThat(urls.get(0).get("contentLengthBytes").asLong()).isEqualTo(partSize);
    assertThat(urls.get(2).get("contentLengthBytes").asLong()).isEqualTo(lastPart);
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/parts")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partNumbers\":[4]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PART_NUMBERS"));

    // "bad connection": parts 1 and 3 make it, part 2 is lost — resume shows exactly that
    fakeStorage.receivePart(uploadId, 1, partSize);
    fakeStorage.receivePart(uploadId, 3, lastPart);
    mockMvc
        .perform(get("/api/v1/videos/" + id + "/parts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalParts").value(3))
        .andExpect(jsonPath("$.uploaded.length()").value(2));

    // completing with a missing part is refused
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/complete-multipart")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UPLOAD_NOT_COMPLETED"));

    // client re-requests part 2's URL and finishes
    fakeStorage.receivePart(uploadId, 2, partSize);
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/complete-multipart")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    assertThat(publisher.lastVideoId).isEqualTo(UUID.fromString(id));

    // session consumed: the resume endpoint now reports no active upload
    mockMvc
        .perform(get("/api/v1/videos/" + id + "/parts").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_UPLOAD"));
  }

  @Test
  void multipartCompleteRejectsSizeMismatchAndAbortDiscards() throws Exception {
    String token = registerConfirmLogin("multipart2@test.com");
    long size = 10_000_000L; // 2 parts
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos/multipart")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                Map.of(
                                    "title", "Errado",
                                    "sizeBytes", size,
                                    "contentType", "video/mp4"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String uploadId = fakeStorage.onlyOpenUploadId();

    // both parts present but short: assembled size != declared -> refused, object discarded
    fakeStorage.receivePart(uploadId, 1, 1_000L);
    fakeStorage.receivePart(uploadId, 2, 1_000L);
    mockMvc
        .perform(
            post("/api/v1/videos/" + id + "/complete-multipart")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UPLOAD_NOT_COMPLETED"));

    // abort clears the session (idempotence is not promised: second call is a 409)
    mockMvc
        .perform(
            delete("/api/v1/videos/" + id + "/multipart").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete("/api/v1/videos/" + id + "/multipart").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_UPLOAD"));
  }

  @Autowired private com.streamtube.application.port.out.StorageCleanupQueue cleanupQueue;
  @Autowired private com.streamtube.domain.video.VideoRepository videoRepository;

  @Test
  void deleteVideoRemovesRowSocialRowsAndStorageArtifacts() throws Exception {
    String token = registerConfirmLogin("delete-owner@test.com");
    String other = registerConfirmLogin("delete-other@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Para apagar"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();
    markReady(slug);
    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // give it social artifacts: a comment (with reply) and a reaction
    JsonNode comment =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos/" + id + "/comments")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "vai sumir"))))
                .andExpect(status().isCreated()));
    String commentId = comment.get("id").asText();
    mockMvc
        .perform(
            put("/api/v1/videos/" + id + "/reaction")
                .header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LIKE\"}"))
        .andExpect(status().isNoContent());

    // non-owner cannot delete
    mockMvc
        .perform(delete("/api/v1/videos/" + id).header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());

    // owner deletes: row + cascaded social rows gone, every read 404
    mockMvc
        .perform(delete("/api/v1/videos/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/v1/videos/" + slug)).andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/comments"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/comments/" + commentId + "/replies"))
        .andExpect(status().isNotFound());
    // second delete: the resource is gone — 404 is the truthful answer
    mockMvc
        .perform(delete("/api/v1/videos/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());

    // drain the outbox the way the worker's sweeper does and assert the exact prefix set
    new com.streamtube.application.video.ProcessStorageCleanupsUseCase(cleanupQueue, fakeStorage)
        .execute();
    assertThat(fakeStorage.deletedPrefixes)
        .contains("videos/" + slug, "thumbnails/" + slug, "hls/" + slug + "/");
  }

  @Test
  void staleDraftIsPurgedBySweeperRules() throws Exception {
    String token = registerConfirmLogin("stale-draft@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Abandonado"))))
                .andExpect(status().isCreated()));
    String slug = init.get("slug").asText();

    // initiate happened 8 days "ago"
    try (Connection c = dataSource.getConnection();
        var st =
            c.prepareStatement(
                "UPDATE videos SET created_at = now() - interval '8 days' WHERE slug = ?")) {
      st.setString(1, slug);
      st.executeUpdate();
    }

    int purged =
        new com.streamtube.application.video.PurgeStaleUploadsUseCase(
                videoRepository, fakeStorage, cleanupQueue, java.time.Clock.systemUTC(), 7)
            .execute();

    assertThat(purged).isGreaterThanOrEqualTo(1);
    mockMvc
        .perform(get("/api/v1/videos/" + slug).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void hlsPlaylistsAreServedThroughTheApiWithTheVisibilityMatrix() throws Exception {
    String token = registerConfirmLogin("hls@test.com");
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody("Com HLS"))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();

    // simulate the worker: READY + ladder in storage + hls_master_key on the row
    markReady(slug);
    try (Connection c = dataSource.getConnection();
        var st =
            c.prepareStatement("UPDATE videos SET hls_master_key = ? WHERE slug = ?")) {
      st.setString(1, "hls/" + slug + "/master.m3u8");
      st.setString(2, slug);
      st.executeUpdate();
    }
    fakeStorage.putTextObject(
        "hls/" + slug + "/master.m3u8",
        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=928000\n360p/playlist.m3u8\n");
    fakeStorage.putTextObject(
        "hls/" + slug + "/360p/playlist.m3u8",
        "#EXTM3U\n#EXTINF:6.0,\nseg-000.ts\n#EXT-X-ENDLIST\n");

    // draft: 404 for anonymous, owner plays without counting a view
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/hls/master.m3u8"))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/videos/" + slug + "/hls/master.m3u8")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/videos/" + slug).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.views").value(0))
        .andExpect(jsonPath("$.hlsUrl").value("/api/v1/videos/" + slug + "/hls/master.m3u8"));

    mockMvc
        .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // published: master rewrites renditions to API paths and counts ONE view
    String master =
        mockMvc
            .perform(get("/api/v1/videos/" + slug + "/hls/master.m3u8"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(master)
        .contains("#EXT-X-STREAM-INF:BANDWIDTH=928000")
        .contains("/api/v1/videos/" + slug + "/hls/360p/playlist.m3u8");

    // rendition playlist: segments presigned with the long TTL; does NOT count views
    String playlist =
        mockMvc
            .perform(get("/api/v1/videos/" + slug + "/hls/360p/playlist.m3u8"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(playlist)
        .contains("#EXTINF:6.0,")
        .contains("hls/" + slug + "/360p/seg-000.ts?stream&ttl=21600");
    mockMvc
        .perform(get("/api/v1/videos/" + slug))
        .andExpect(jsonPath("$.views").value(1)); // only the master fetch counted

    // unknown rendition of a real ladder -> 404
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/hls/720p/playlist.m3u8"))
        .andExpect(status().isNotFound());
  }

  @Test
  void videosWithoutHlsKeepTheProgressiveFallback() throws Exception {
    String token = registerConfirmLogin("no-hls@test.com");
    String slug = createCategorizedVideo(token, "Sem HLS", null, null, true);

    mockMvc
        .perform(get("/api/v1/videos/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hlsUrl").doesNotExist());
    mockMvc
        .perform(get("/api/v1/videos/" + slug + "/hls/master.m3u8"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/videos/" + slug + "/stream")).andExpect(status().isFound());
  }

  @Test
  void homeGridListsOnlyPublishedPublicNewestFirst() throws Exception {
    String token = registerConfirmLogin("home-grid@test.com");
    JsonNode categories =
        readJson(mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk()));
    String categoryA = categories.get(2).get("id").asText();
    String categoryB = categories.get(3).get("id").asText();

    String publicA = createCategorizedVideo(token, "Home A", categoryA, null, true);
    String publicB = createCategorizedVideo(token, "Home B", categoryB, null, true);
    String unlisted = createCategorizedVideo(token, "Home unlisted", categoryA, "UNLISTED", true);
    String draft = createCategorizedVideo(token, "Home draft", categoryA, null, false);

    // global grid: published PUBLIC in, UNLISTED/draft out; cards carry the channel identity
    JsonNode home =
        readJson(mockMvc.perform(get("/api/v1/videos?size=100")).andExpect(status().isOk()));
    java.util.List<String> slugs = home.findValuesAsText("slug");
    assertThat(slugs).contains(publicA, publicB).doesNotContain(unlisted, draft);
    JsonNode first = home.get("items").get(0);
    assertThat(first.get("channel").get("nickname").asText()).isNotBlank();
    assertThat(first.get("publishedAt").asText()).isNotBlank();
    assertThat(first.get("views").asLong()).isGreaterThanOrEqualTo(0);

    // newest publication first (parse: ISO strings with varying fraction lengths don't sort)
    java.util.List<java.time.Instant> publishedAts =
        home.findValuesAsText("publishedAt").stream().map(java.time.Instant::parse).toList();
    assertThat(publishedAts).isSortedAccordingTo(java.util.Comparator.reverseOrder());

    // category filter: only category A's public video (of ours)
    JsonNode filtered =
        readJson(
            mockMvc
                .perform(get("/api/v1/videos?size=100&categoryId=" + categoryA))
                .andExpect(status().isOk()));
    assertThat(filtered.findValuesAsText("slug"))
        .contains(publicA)
        .doesNotContain(publicB, unlisted, draft);

    // unknown category: empty page, not an error
    mockMvc
        .perform(get("/api/v1/videos?categoryId=" + UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));
  }

  @Test
  void searchMatchesTitleOrChannelNameOfListedVideosOnly() throws Exception {
    String token = registerConfirmLogin("search-owner@test.com");
    mockMvc
        .perform(
            patch("/api/v1/channels/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Estúdio Quux"))))
        .andExpect(status().isOk());

    String byTitle = createCategorizedVideo(token, "Xyzzy Plugh Tutorial", null, null, true);
    String hiddenDraft = createCategorizedVideo(token, "Xyzzy Plugh secreto", null, null, false);
    String unlisted =
        createCategorizedVideo(token, "Xyzzy Plugh unlisted", null, "UNLISTED", true);
    String withPercent = createCategorizedVideo(token, "Aula 100% completa", null, null, true);

    // by title, case-insensitive contains — drafts and UNLISTED never match
    JsonNode byTitleResult =
        readJson(
            mockMvc
                .perform(get("/api/v1/search").param("q", "xyzzy plugh"))
                .andExpect(status().isOk()));
    assertThat(byTitleResult.findValuesAsText("slug"))
        .contains(byTitle)
        .doesNotContain(hiddenDraft, unlisted);

    // by channel name: every listed video of the matching channel
    JsonNode byChannel =
        readJson(
            mockMvc.perform(get("/api/v1/search").param("q", "quux")).andExpect(status().isOk()));
    assertThat(byChannel.findValuesAsText("slug")).contains(byTitle, withPercent);

    // % in the query is literal text, not a wildcard
    JsonNode literal =
        readJson(
            mockMvc.perform(get("/api/v1/search").param("q", "100%")).andExpect(status().isOk()));
    assertThat(literal.findValuesAsText("slug")).contains(withPercent).doesNotContain(byTitle);
    mockMvc
        .perform(get("/api/v1/search").param("q", "00%x"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(0));

    // short query -> 400
    mockMvc
        .perform(get("/api/v1/search").param("q", "a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
  }

  /** Initiates a video, assigns a category (and visibility), marks READY and optionally publishes. */
  private String createCategorizedVideo(
      String token, String title, String categoryId, String visibility, boolean publish)
      throws Exception {
    JsonNode init =
        readJson(
            mockMvc
                .perform(
                    post("/api/v1/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initiateBody(title))))
                .andExpect(status().isCreated()));
    String id = init.get("id").asText();
    String slug = init.get("slug").asText();

    Map<String, Object> patch = new java.util.HashMap<>();
    patch.put("categoryId", categoryId);
    if (visibility != null) {
      patch.put("visibility", visibility);
    }
    mockMvc
        .perform(
            patch("/api/v1/videos/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patch)))
        .andExpect(status().isOk());

    markReady(slug);
    if (publish) {
      mockMvc
          .perform(post("/api/v1/videos/" + id + "/publish").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
    }
    return slug;
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
