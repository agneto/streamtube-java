package com.streamtube.api.web.dto;

import com.streamtube.domain.video.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for the video endpoints. */
public final class VideoDtos {

  private VideoDtos() {}

  /** The declared size/type are signed into the upload URL; storage rejects mismatches. */
  public record CreateVideoRequest(
      @NotBlank @Size(max = 255) String title,
      @NotNull @Positive Long sizeBytes,
      @NotBlank String contentType) {}

  /** PATCH semantics: absent/null fields are left untouched; a blank description clears it. */
  public record UpdateVideoRequest(
      @Size(max = 255) String title,
      @Size(max = 5000) String description,
      UUID categoryId,
      Visibility visibility) {}

  /** The declared size/type are signed into the upload URL; storage rejects mismatches. */
  public record ThumbnailUploadRequest(
      @NotNull @Positive Long sizeBytes, @NotBlank String contentType) {}

  public record ThumbnailUploadResponse(String uploadUrl) {}

  public record InitiateUploadResponse(UUID id, String slug, String uploadUrl) {}

  public record VideoInfoResponse(
      UUID id,
      String slug,
      String title,
      String status,
      String description,
      UUID categoryId,
      String visibility,
      Instant publishedAt,
      String thumbnailUrl,
      Double durationSeconds,
      long views,
      UUID channelId,
      Instant createdAt) {}

  /** Listing item for channel video pages (owner panel, public channel page) and suggestions. */
  public record VideoSummaryResponse(
      UUID id,
      String slug,
      String title,
      String status,
      String visibility,
      Instant publishedAt,
      String thumbnailUrl,
      Double durationSeconds,
      long views,
      Instant createdAt) {}
}
