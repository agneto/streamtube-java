package com.streamtube.infrastructure.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistence model for a comment. {@code video_id}/{@code user_id}/{@code parent_id} are plain
 * UUID columns (DB-level FKs only). Counters are {@code updatable = false}: only the atomic
 * "± 1" statements may change them.
 */
@Entity
@Table(name = "comments")
public class CommentEntity {

  @Id private UUID id;

  @Column(name = "video_id", nullable = false)
  private UUID videoId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(nullable = false, length = 2000)
  private String content;

  @Column(name = "likes_count", nullable = false, updatable = false)
  private long likesCount;

  @Column(name = "dislikes_count", nullable = false, updatable = false)
  private long dislikesCount;

  @Column(name = "replies_count", nullable = false, updatable = false)
  private long repliesCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentEntity() {}

  public CommentEntity(
      UUID id,
      UUID videoId,
      UUID userId,
      UUID parentId,
      String content,
      long likesCount,
      long dislikesCount,
      long repliesCount,
      Instant createdAt) {
    this.id = id;
    this.videoId = videoId;
    this.userId = userId;
    this.parentId = parentId;
    this.content = content;
    this.likesCount = likesCount;
    this.dislikesCount = dislikesCount;
    this.repliesCount = repliesCount;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getVideoId() {
    return videoId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getParentId() {
    return parentId;
  }

  public String getContent() {
    return content;
  }

  public long getLikesCount() {
    return likesCount;
  }

  public long getDislikesCount() {
    return dislikesCount;
  }

  public long getRepliesCount() {
    return repliesCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
