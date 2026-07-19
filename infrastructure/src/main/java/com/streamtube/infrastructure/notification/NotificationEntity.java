package com.streamtube.infrastructure.notification;

import com.streamtube.domain.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a notification. {@code recipient_user_id}/{@code actor_channel_id}/{@code
 * video_id}/{@code comment_id} are plain UUID columns (DB-level FKs only, all cascading). This
 * entity is mapped by the API persistence unit only (registered next to {@code
 * infrastructure.social}); the worker never sees it.
 */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

  @Id private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private NotificationType type;

  @Column(name = "actor_channel_id")
  private UUID actorChannelId;

  @Column(name = "video_id")
  private UUID videoId;

  @Column(name = "comment_id")
  private UUID commentId;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NotificationEntity() {}

  public NotificationEntity(
      UUID id,
      UUID recipientUserId,
      NotificationType type,
      UUID actorChannelId,
      UUID videoId,
      UUID commentId,
      Instant readAt,
      Instant createdAt) {
    this.id = id;
    this.recipientUserId = recipientUserId;
    this.type = type;
    this.actorChannelId = actorChannelId;
    this.videoId = videoId;
    this.commentId = commentId;
    this.readAt = readAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public NotificationType getType() {
    return type;
  }

  public UUID getActorChannelId() {
    return actorChannelId;
  }

  public UUID getVideoId() {
    return videoId;
  }

  public UUID getCommentId() {
    return commentId;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
