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

/**
 * Persistence model for a user's reaction to a video. Part of the social slice: API-only, kept out
 * of the worker's persistence unit (this package is not in the worker's scan). All mutations go
 * through native statements on {@link VideoReactionJpaRepository}.
 */
@Entity
@Table(name = "video_reactions")
@IdClass(VideoReactionEntity.Key.class)
public class VideoReactionEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "video_id")
  private UUID videoId;

  @Column(nullable = false, length = 7)
  private String type;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected VideoReactionEntity() {}

  public static class Key implements Serializable {
    private UUID userId;
    private UUID videoId;

    public Key() {}

    public Key(UUID userId, UUID videoId) {
      this.userId = userId;
      this.videoId = videoId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(userId, k.userId)
          && Objects.equals(videoId, k.videoId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, videoId);
    }
  }
}
