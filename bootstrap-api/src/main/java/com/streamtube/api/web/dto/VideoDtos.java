package com.streamtube.api.web.dto;

import com.streamtube.domain.video.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
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

  /** Multipart session opened: the client slices the file into {@code totalParts} parts. */
  public record MultipartInitiateResponse(
      UUID id, String slug, long partSizeBytes, int totalParts) {}

  public record PartUrlsRequest(@NotEmpty List<Integer> partNumbers) {}

  /** Presigned URL for one part; its exact {@code contentLengthBytes} is signed into it. */
  public record PartUrlResponse(int partNumber, String url, long contentLengthBytes) {}

  /** Resume status: which parts are already in storage. */
  public record UploadedPartsResponse(
      long partSizeBytes, int totalParts, List<UploadedPartItem> uploaded) {}

  public record UploadedPartItem(int partNumber, long sizeBytes) {}

  /** {@code myReaction} is resolved only on the info read (null on write responses/anonymous). */
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
      long likes,
      long dislikes,
      long commentsCount,
      String myReaction,
      UUID channelId,
      Instant createdAt) {}

  /** Home-grid / search card: embeds the channel identity (unlike the channel-page summary). */
  public record VideoCardResponse(
      UUID id,
      String slug,
      String title,
      String thumbnailUrl,
      Double durationSeconds,
      long views,
      Instant publishedAt,
      UUID categoryId,
      ChannelRefResponse channel) {}

  public record ChannelRefResponse(UUID id, String name, String nickname) {}

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
