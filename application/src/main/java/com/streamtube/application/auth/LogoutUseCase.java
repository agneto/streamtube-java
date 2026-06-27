package com.streamtube.application.auth;

import com.streamtube.application.port.out.RefreshTokenService;
import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.RefreshTokenRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revokes the presented refresh token (idempotent; unknown tokens are a no-op). */
@Service
public class LogoutUseCase {

  private final RefreshTokenRepository refreshTokenRepository;
  private final RefreshTokenService refreshTokenService;
  private final Clock clock;

  public LogoutUseCase(
      RefreshTokenRepository refreshTokenRepository,
      RefreshTokenService refreshTokenService,
      Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenService = refreshTokenService;
    this.clock = clock;
  }

  @Transactional
  public void execute(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    String hash = refreshTokenService.hash(rawRefreshToken);
    Optional<RefreshToken> maybe = refreshTokenRepository.findByTokenHash(hash);
    maybe.ifPresent(
        token -> {
          token.revoke(clock.instant());
          refreshTokenRepository.save(token);
        });
  }
}
