package com.streamtube.application.auth;

import com.streamtube.application.auth.result.TokenPair;
import com.streamtube.application.port.out.AccessTokenService;
import com.streamtube.application.port.out.AccessTokenService.IssuedAccessToken;
import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.RefreshTokenService;
import com.streamtube.application.port.out.RefreshTokenService.IssuedRefreshToken;
import com.streamtube.domain.auth.RefreshToken;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.domain.shared.AuthExceptions.EmailNotConfirmedException;
import com.streamtube.domain.shared.AuthExceptions.InvalidCredentialsException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates a user and issues a fresh access + refresh token pair (new token family). */
@Service
public class LoginUseCase {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordHasher passwordHasher;
  private final AccessTokenService accessTokenService;
  private final RefreshTokenService refreshTokenService;
  private final Clock clock;

  public LoginUseCase(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordHasher passwordHasher,
      AccessTokenService accessTokenService,
      RefreshTokenService refreshTokenService,
      Clock clock) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordHasher = passwordHasher;
    this.accessTokenService = accessTokenService;
    this.refreshTokenService = refreshTokenService;
    this.clock = clock;
  }

  @Transactional
  public TokenPair execute(String email, String rawPassword) {
    User user =
        userRepository
            .findByEmail(email.trim().toLowerCase())
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    if (!user.isConfirmed()) {
      throw new EmailNotConfirmedException();
    }

    Instant now = clock.instant();
    UUID family = UUID.randomUUID();
    UUID jti = UUID.randomUUID();
    IssuedRefreshToken refresh = refreshTokenService.issue(user.id(), family, jti);
    refreshTokenRepository.save(
        RefreshToken.issue(
            UUID.randomUUID(),
            user.id(),
            family,
            jti,
            refresh.tokenHash(),
            refresh.expiresAt(),
            now));

    IssuedAccessToken access = accessTokenService.issue(user.id(), user.email());
    return new TokenPair(access.token(), access.expiresInSeconds(), refresh.rawValue());
  }
}
