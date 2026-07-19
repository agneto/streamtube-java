package com.streamtube.application.notification.result;

import com.streamtube.domain.notification.NotificationFeedRow;
import java.time.Instant;
import java.util.UUID;

/**
 * Feed projection for one notification. The actor is always a channel identity (never a user id);
 * {@code video} and {@code comment} are present only for the types that reference them. The video
 * thumbnail is presigned in the use case, matching {@code VideoInfoView}/{@code CommentView}.
 */
public record NotificationView(
    UUID id,
    String type,
    boolean read,
    Instant createdAt,
    Actor actor,
    VideoRef video,
    CommentRef comment) {

  public record Actor(UUID channelId, String name, String nickname) {}

  public record VideoRef(UUID id, String slug, String title, String thumbnailUrl) {}

  public record CommentRef(UUID id, String content) {}

  public static NotificationView from(NotificationFeedRow row, String thumbnailUrl) {
    Actor actor =
        row.actorChannelId() == null
            ? null
            : new Actor(row.actorChannelId(), row.actorName(), row.actorNickname());
    VideoRef video =
        row.videoId() == null
            ? null
            : new VideoRef(row.videoId(), row.videoSlug(), row.videoTitle(), thumbnailUrl);
    CommentRef comment =
        row.commentId() == null ? null : new CommentRef(row.commentId(), row.commentContent());
    return new NotificationView(
        row.id(), row.type().name(), row.readAt() != null, row.createdAt(), actor, video, comment);
  }
}
