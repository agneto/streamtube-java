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

/** Persistence model for a channel subscription (user → channel link). */
@Entity
@Table(name = "subscriptions")
@IdClass(SubscriptionEntity.Key.class)
public class SubscriptionEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "channel_id")
  private UUID channelId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SubscriptionEntity() {}

  public UUID getUserId() {
    return userId;
  }

  public UUID getChannelId() {
    return channelId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Key implements Serializable {
    private UUID userId;
    private UUID channelId;

    public Key() {}

    public Key(UUID userId, UUID channelId) {
      this.userId = userId;
      this.channelId = channelId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(userId, k.userId)
          && Objects.equals(channelId, k.channelId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, channelId);
    }
  }
}
