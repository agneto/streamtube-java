package com.streamtube.domain.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Read projection for one feed row: the notification plus the joined display fields (actor channel,
 * video, comment). Storage keys are returned raw — the use case presigns {@code videoThumbnailKey}
 * before it leaves the application layer, matching {@code VideoInfoView}/{@code CommentView}. Any
 * joined subject may be {@code null} (the FK column is nullable per type). {@code readAt == null}
 * means unread; the read flag is derived from it in the view.
 */
public record NotificationFeedRow(
    UUID id,
    NotificationType type,
    Instant readAt,
    Instant createdAt,
    UUID actorChannelId,
    String actorName,
    String actorNickname,
    UUID videoId,
    String videoSlug,
    String videoTitle,
    String videoThumbnailKey,
    UUID commentId,
    String commentContent) {}
