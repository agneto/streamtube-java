package com.streamtube.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for the video endpoints. */
public final class VideoDtos {

  private VideoDtos() {}

  public record CreateVideoRequest(@NotBlank @Size(max = 255) String title) {}

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
