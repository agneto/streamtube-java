package com.streamtube.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Output port that mints opaque refresh-token secrets and hashes them for storage. The raw value is
 * returned to the client once; only its hash is persisted.
 */
public interface RefreshTokenService {

  IssuedRefreshToken issue(UUID userId, UUID family, UUID jti);

  String hash(String rawToken);

  record IssuedRefreshToken(String rawValue, String tokenHash, Instant expiresAt) {}
}
