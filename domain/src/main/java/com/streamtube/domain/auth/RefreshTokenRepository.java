package com.streamtube.domain.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port for refresh-token persistence. */
public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken token);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamily(UUID family);

  void revokeFamily(UUID family, java.time.Instant now);

  /**
   * Deletes every refresh token of the user. Deletion (not revocation) is deliberate: a revoked
   * token still rotates within the reuse-detection grace period, which would let a stolen token
   * survive a password reset.
   */
  void deleteAllForUser(UUID userId);
}
