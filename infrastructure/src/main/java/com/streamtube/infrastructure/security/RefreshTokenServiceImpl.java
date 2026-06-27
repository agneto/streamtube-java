package com.streamtube.infrastructure.security;

import com.streamtube.application.port.out.RefreshTokenService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues opaque refresh-token secrets and hashes them for storage. */
@Component
public class RefreshTokenServiceImpl implements RefreshTokenService {

  private final long ttlSeconds;
  private final Clock clock;

  public RefreshTokenServiceImpl(
      @Value("${auth.refresh.ttl-seconds}") long ttlSeconds, Clock clock) {
    this.ttlSeconds = ttlSeconds;
    this.clock = clock;
  }

  @Override
  public IssuedRefreshToken issue(UUID userId, UUID family, UUID jti) {
    String raw = SecureTokens.randomRawValue();
    return new IssuedRefreshToken(
        raw, SecureTokens.sha256Hex(raw), clock.instant().plusSeconds(ttlSeconds));
  }

  @Override
  public String hash(String rawToken) {
    return SecureTokens.sha256Hex(rawToken);
  }
}
