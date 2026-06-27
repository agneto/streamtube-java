package com.streamtube.domain.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted, hashed refresh token. Tokens are grouped into a {@code family} so that rotation can
 * detect reuse: when a revoked token is presented outside the grace period, the whole family is
 * revoked.
 */
public class RefreshToken {

  private final UUID id;
  private final UUID userId;
  private final UUID family;
  private final UUID jti;
  private final String tokenHash;
  private final Instant expiresAt;
  private Instant revokedAt;
  private final Instant createdAt;

  public RefreshToken(
      UUID id,
      UUID userId,
      UUID family,
      UUID jti,
      String tokenHash,
      Instant expiresAt,
      Instant revokedAt,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.family = family;
    this.jti = jti;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.createdAt = createdAt;
  }

  public static RefreshToken issue(
      UUID id,
      UUID userId,
      UUID family,
      UUID jti,
      String tokenHash,
      Instant expiresAt,
      Instant now) {
    return new RefreshToken(id, userId, family, jti, tokenHash, expiresAt, null, now);
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public void revoke(Instant now) {
    if (this.revokedAt == null) {
      this.revokedAt = now;
    }
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public UUID family() {
    return family;
  }

  public UUID jti() {
    return jti;
  }

  public String tokenHash() {
    return tokenHash;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
