package com.streamtube.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channels")
public class ChannelEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 50, unique = true)
  private String nickname;

  @Column private String description;

  // updatable = false: only the atomic "subscribers_count ± 1" statements (social slice) may
  // change the counter — a regular save() flushing a stale value would erase concurrent updates.
  @Column(name = "subscribers_count", nullable = false, updatable = false)
  private long subscribersCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChannelEntity() {}

  public ChannelEntity(
      UUID id,
      UUID userId,
      String name,
      String nickname,
      String description,
      long subscribersCount,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.nickname = nickname;
    this.description = description;
    this.subscribersCount = subscribersCount;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public String getNickname() {
    return nickname;
  }

  public String getDescription() {
    return description;
  }

  public long getSubscribersCount() {
    return subscribersCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
