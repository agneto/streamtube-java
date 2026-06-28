package com.streamtube.domain.auth;

import java.time.Instant;
import java.util.UUID;

/** A single-use, hashed token for email confirmation or password reset. */
public class VerificationToken {

  private final UUID id;
  private final UUID userId;
  private final VerificationTokenType type;
  private final String tokenHash;
  private final Instant expiresAt;
  private Instant consumedAt;
  private final Instant createdAt;

  public VerificationToken(
      UUID id,
      UUID userId,
      VerificationTokenType type,
      String tokenHash,
      Instant expiresAt,
      Instant consumedAt,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.type = type;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.createdAt = createdAt;
  }

  public static VerificationToken issue(
      UUID id,
      UUID userId,
      VerificationTokenType type,
      String tokenHash,
      Instant expiresAt,
      Instant now) {
    return new VerificationToken(id, userId, type, tokenHash, expiresAt, null, now);
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }

  public void consume(Instant now) {
    this.consumedAt = now;
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public VerificationTokenType type() {
    return type;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant consumedAt() {
    return consumedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
