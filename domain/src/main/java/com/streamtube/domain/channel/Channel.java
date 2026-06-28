package com.streamtube.domain.channel;

import java.time.Instant;
import java.util.UUID;

/** Pure domain entity for a user's channel (1:1 with {@code User}). */
public class Channel {

  private final UUID id;
  private final UUID userId;
  private String name;
  private String nickname;
  private String description;
  private final Instant createdAt;
  private Instant updatedAt;

  public Channel(
      UUID id,
      UUID userId,
      String name,
      String nickname,
      String description,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.nickname = nickname;
    this.description = description;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Factory for the channel auto-created when a user registers. */
  public static Channel createForUser(
      UUID id, UUID userId, String name, String nickname, Instant now) {
    return new Channel(id, userId, name, nickname, null, now, now);
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public String name() {
    return name;
  }

  public String nickname() {
    return nickname;
  }

  public String description() {
    return description;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
