package com.streamtube.domain.notification;

import com.streamtube.domain.shared.PageResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Output port for the notification feed. Writes happen inside the triggering transaction (a
 * notification shares the fate of the comment/subscribe that caused it); cleanup is FK
 * {@code ON DELETE CASCADE}, so there is no delete/sweep method here.
 */
public interface NotificationRepository {

  /** Single-recipient insert (NEW_SUBSCRIBER / VIDEO_COMMENT / COMMENT_REPLY). */
  void create(Notification notification);

  /**
   * NEW_VIDEO fan-out: one set-based {@code INSERT INTO notifications ... SELECT ... FROM
   * subscriptions WHERE channel_id = :channelId} — a row per subscriber, never a per-subscriber
   * loop. The publisher is not subscribed to their own channel, so they are excluded naturally.
   *
   * @return the number of subscribers notified
   */
  int fanOutNewVideo(UUID channelId, UUID videoId, Instant at);

  long unreadCount(UUID recipientUserId);

  /**
   * Marks one notification read, scoped to its recipient.
   *
   * @return {@code true} only if the row exists, belongs to {@code recipientUserId} and was unread
   *     — a foreign or already-read id flips nothing (no IDOR)
   */
  boolean markRead(UUID id, UUID recipientUserId);

  /**
   * Marks every unread notification of the recipient read.
   *
   * @return the number of rows flipped
   */
  int markAllRead(UUID recipientUserId);

  /** Recipient's feed, newest first, with actor/video/comment display fields joined in. */
  PageResult<NotificationFeedRow> findPage(UUID recipientUserId, int page, int size);
}
