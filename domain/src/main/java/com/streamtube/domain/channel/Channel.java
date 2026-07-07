package com.streamtube.domain.channel;

import com.streamtube.domain.shared.ChannelExceptions;

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

  /**
   * Updates the channel description. The description is optional: {@code null} or blank clears it.
   * Domain invariant: at most 5000 characters.
   */
  public void updateDescription(String newDescription, Instant now) {
    if (newDescription != null && newDescription.length() > 5000) {
      throw new ChannelExceptions.InvalidChannelDescriptionException();
    }
    this.description = newDescription;
    this.updatedAt = now;
  }

  /** Renames the channel. Domain invariant: non-blank, at most 50 chars (column limit). */
  public void rename(String newName, Instant now) {
    if (newName == null || newName.isBlank() || newName.trim().length() > 50) {
      throw new ChannelExceptions.InvalidChannelNameException();
    }
    this.name = newName.trim();
    this.updatedAt = now;
  }

  /**
   * Changes the public nickname (part of the channel URL). Invariant: 3–50 chars of
   * {@code [a-zA-Z0-9_-]}. Uniqueness is enforced by the repository/DB constraint. Old URLs are
   * not redirected — accepted trade-off recorded in the Phase 04 plan.
   */
  public void changeNickname(String newNickname, Instant now) {
    if (newNickname == null || !newNickname.matches("[a-zA-Z0-9_-]{3,50}")) {
      throw new ChannelExceptions.InvalidNicknameException();
    }
    this.nickname = newNickname;
    this.updatedAt = now;
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
