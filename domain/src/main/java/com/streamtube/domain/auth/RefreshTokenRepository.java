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
}
