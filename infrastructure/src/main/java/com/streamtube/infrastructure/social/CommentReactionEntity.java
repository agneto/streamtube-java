package com.streamtube.infrastructure.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence model for a user's reaction to a comment — same shape as the video reaction. */
@Entity
@Table(name = "comment_reactions")
@IdClass(CommentReactionEntity.Key.class)
public class CommentReactionEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "comment_id")
  private UUID commentId;

  @Column(nullable = false, length = 7)
  private String type;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CommentReactionEntity() {}

  public static class Key implements Serializable {
    private UUID userId;
    private UUID commentId;

    public Key() {}

    public Key(UUID userId, UUID commentId) {
      this.userId = userId;
      this.commentId = commentId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(userId, k.userId)
          && Objects.equals(commentId, k.commentId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, commentId);
    }
  }
}
