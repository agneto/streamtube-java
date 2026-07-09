package com.streamtube.application.video.result;

import com.streamtube.domain.social.ReactionType;
import com.streamtube.domain.video.Video;
import java.time.Instant;
import java.util.UUID;

/** {@code myReaction} is only resolved on the info read; write responses leave it null. */
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
    long likes,
    long dislikes,
    long commentsCount,
    String myReaction,
    UUID channelId,
    Instant createdAt) {

  public static VideoInfoView from(Video video, String thumbnailUrl) {
    return from(video, thumbnailUrl, null);
  }

  public static VideoInfoView from(Video video, String thumbnailUrl, ReactionType myReaction) {
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
        video.likesCount(),
        video.dislikesCount(),
        video.commentsCount(),
        myReaction == null ? null : myReaction.name(),
        video.channelId(),
        video.createdAt());
  }
}
