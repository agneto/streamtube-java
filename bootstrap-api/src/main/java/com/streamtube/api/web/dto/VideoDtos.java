package com.streamtube.api.web.dto;

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

  public record UpdateVideoRequest(@NotBlank @Size(max = 255) String title) {}

  public record InitiateUploadResponse(UUID id, String slug, String uploadUrl) {}

  public record VideoInfoResponse(
      UUID id,
      String slug,
      String title,
      String status,
      String thumbnailUrl,
      Double durationSeconds,
      UUID channelId,
      Instant createdAt) {}
}
