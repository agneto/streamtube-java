package com.streamtube.application.video.result;

import com.streamtube.domain.video.Video;
import java.time.Instant;
import java.util.UUID;

/** Listing item for channel video pages (owner panel and public channel page). */
public record VideoSummaryView(
    UUID id,
    String slug,
    String title,
    String status,
    String visibility,
    Instant publishedAt,
    String thumbnailUrl,
    Double durationSeconds,
    Instant createdAt) {

  public static VideoSummaryView from(Video video, String thumbnailUrl) {
    return new VideoSummaryView(
        video.id(),
        video.slug(),
        video.title(),
        video.status().name(),
        video.visibility().name(),
        video.publishedAt(),
        thumbnailUrl,
        video.durationSeconds(),
        video.createdAt());
  }
}
