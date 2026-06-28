package com.streamtube.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Pure domain entity for a platform user. No framework annotations. */
public class User {

  private final UUID id;
  private final String email;
  private String passwordHash;
  private boolean confirmed;
  private final Instant createdAt;
  private Instant updatedAt;

  public User(
      UUID id,
      String email,
      String passwordHash,
      boolean confirmed,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.confirmed = confirmed;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Factory for a brand-new, unconfirmed user. */
  public static User register(UUID id, String email, String passwordHash, Instant now) {
    return new User(id, email, passwordHash, false, now, now);
  }

  public void confirm(Instant now) {
    this.confirmed = true;
    this.updatedAt = now;
  }

  public void changePassword(String newPasswordHash, Instant now) {
    this.passwordHash = newPasswordHash;
    this.updatedAt = now;
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public boolean isConfirmed() {
    return confirmed;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
