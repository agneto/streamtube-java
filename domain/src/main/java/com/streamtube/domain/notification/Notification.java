package com.streamtube.domain.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Pure domain entity for one feed item. Referential (not snapshotted): {@code actorChannelId},
 * {@code videoId} and {@code commentId} point at the live rows, and display fields (name,
 * thumbnail…) are resolved by join at read time. {@code readAt == null} means unread.
 *
 * <p>The {@code recipient} is always a <em>user</em>; the {@code actor} is always a <em>channel</em>
 * — never conflate the two ids. Single-recipient events use these factories; {@code NEW_VIDEO}
 * fan-out is a set-based insert on the repository, not a per-recipient record.
 */
public record Notification(
    UUID id,
    UUID recipientUserId,
    NotificationType type,
    UUID actorChannelId,
    UUID videoId,
    UUID commentId,
    Instant readAt,
    Instant createdAt) {

  /** Recipient = the channel owner; actor = the subscriber's channel. */
  public static Notification newSubscriber(
      UUID id, UUID recipientUserId, UUID actorChannelId, Instant now) {
    return new Notification(
        id, recipientUserId, NotificationType.NEW_SUBSCRIBER, actorChannelId, null, null, null, now);
  }

  /** Recipient = the video owner; actor = the commenter's channel. */
  public static Notification videoComment(
      UUID id, UUID recipientUserId, UUID actorChannelId, UUID videoId, UUID commentId, Instant now) {
    return new Notification(
        id,
        recipientUserId,
        NotificationType.VIDEO_COMMENT,
        actorChannelId,
        videoId,
        commentId,
        null,
        now);
  }

  /** Recipient = the parent comment's author; actor = the replier's channel. */
  public static Notification commentReply(
      UUID id, UUID recipientUserId, UUID actorChannelId, UUID videoId, UUID commentId, Instant now) {
    return new Notification(
        id,
        recipientUserId,
        NotificationType.COMMENT_REPLY,
        actorChannelId,
        videoId,
        commentId,
        null,
        now);
  }
}
