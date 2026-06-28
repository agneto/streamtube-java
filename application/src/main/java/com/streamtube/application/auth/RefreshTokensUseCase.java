package com.streamtube.application.auth;

import com.streamtube.application.auth.result.TokenPair;
import com.streamtube.application.port.out.AccessTokenService;
import com.streamtube.application.port.out.AccessTokenService.IssuedAccessToken;
import com.streamtube.application.port.out.RefreshTokenService;
import com.streamtube.application.port.out.RefreshTokenService.IssuedRefreshToken;
import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.domain.shared.AuthExceptions.InvalidTokenException;
import com.streamtube.domain.shared.AuthExceptions.TokenExpiredException;
import com.streamtube.domain.shared.AuthExceptions.TokenReuseDetectedException;
import com.streamtube.domain.shared.AuthExceptions.UserNotFoundException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotates a refresh token. Implements reuse detection with a short grace period:
 *
 * <ul>
 *   <li>unknown hash → invalid; expired → expired
 *   <li>revoked within the grace window → benign retry: rotate normally (same family)
 *   <li>revoked past the grace window → reuse detected → revoke the whole family
 *   <li>valid → revoke current and mint a new token in the same family
 * </ul>
 */
@Service
public class RefreshTokensUseCase {

  private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final AccessTokenService accessTokenService;
  private final RefreshTokenService refreshTokenService;
  private final Clock clock;

  public RefreshTokensUseCase(
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      AccessTokenService accessTokenService,
      RefreshTokenService refreshTokenService,
      Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.accessTokenService = accessTokenService;
    this.refreshTokenService = refreshTokenService;
    this.clock = clock;
  }

  @Transactional
  public TokenPair execute(String rawToken) {
    String hash = refreshTokenService.hash(rawToken);
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidTokenException::new);

    Instant now = clock.instant();
    if (token.isExpired(now)) {
      throw new TokenExpiredException();
    }

    if (token.isRevoked()) {
      boolean withinGrace = !now.isAfter(token.revokedAt().plus(GRACE_PERIOD));
      if (!withinGrace) {
        refreshTokenRepository.revokeFamily(token.family(), now);
        throw new TokenReuseDetectedException();
      }
    }

    return rotate(token, now);
  }

  private TokenPair rotate(RefreshToken current, Instant now) {
    current.revoke(now);
    refreshTokenRepository.save(current);

    User user =
        userRepository.findById(current.userId()).orElseThrow(UserNotFoundException::new);

    UUID newJti = UUID.randomUUID();
    IssuedRefreshToken refresh = refreshTokenService.issue(user.id(), current.family(), newJti);
    refreshTokenRepository.save(
        RefreshToken.issue(
            UUID.randomUUID(),
            user.id(),
            current.family(),
            newJti,
            refresh.tokenHash(),
            refresh.expiresAt(),
            now));

    IssuedAccessToken access = accessTokenService.issue(user.id(), user.email());
    return new TokenPair(access.token(), access.expiresInSeconds(), refresh.rawValue());
  }
}
