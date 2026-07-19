package com.streamtube.domain.notification;

/**
 * The social events that drop a row into a user's notification feed. All four originate on the API
 * side (subscribe, comment, reply, publish); worker-sourced events (VIDEO_READY/FAILED) are a
 * deliberate later evolution — they would require the notifications slice to move into shared
 * persistence.
 */
public enum NotificationType {
  /** Someone subscribed to my channel. Actor: the subscriber's channel. */
  NEW_SUBSCRIBER,
  /** Someone posted a top-level comment on my video. Actor: the commenter's channel. */
  VIDEO_COMMENT,
  /** Someone replied to my comment. Actor: the replier's channel. */
  COMMENT_REPLY,
  /** A channel I subscribe to published a public video. Actor: that channel. */
  NEW_VIDEO
}
