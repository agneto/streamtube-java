package com.streamtube.application.video.result;

import com.streamtube.domain.video.Video;
import java.time.Instant;
import java.util.UUID;

public record VideoInfoView(
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
    Instant createdAt) {

  public static VideoInfoView from(Video video, String thumbnailUrl) {
    return new VideoInfoView(
        video.id(),
        video.slug(),
        video.title(),
        video.status().name(),
        video.description(),
        video.categoryId(),
        video.visibility().name(),
        video.publishedAt(),
        thumbnailUrl,
        video.durationSeconds(),
        video.viewsCount(),
        video.channelId(),
        video.createdAt());
  }
}
