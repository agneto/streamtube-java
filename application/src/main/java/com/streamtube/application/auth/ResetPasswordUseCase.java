package com.streamtube.application.auth;

import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.VerificationTokenService;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.domain.shared.AuthExceptions.InvalidTokenException;
import com.streamtube.domain.shared.AuthExceptions.TokenExpiredException;
import com.streamtube.domain.shared.AuthExceptions.UserNotFoundException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resets a password from a one-time reset token. Deletes every refresh token of the user so a
 * compromised account cannot keep its sessions after the reset (deletion rather than revocation:
 * a revoked token would still rotate within the reuse-detection grace period).
 */
@Service
public class ResetPasswordUseCase {

  private final VerificationTokenRepository verificationTokenRepository;
  private final VerificationTokenService verificationTokenService;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordHasher passwordHasher;
  private final Clock clock;

  public ResetPasswordUseCase(
      VerificationTokenRepository verificationTokenRepository,
      VerificationTokenService verificationTokenService,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordHasher passwordHasher,
      Clock clock) {
    this.verificationTokenRepository = verificationTokenRepository;
    this.verificationTokenService = verificationTokenService;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordHasher = passwordHasher;
    this.clock = clock;
  }

  @Transactional
  public void execute(String rawToken, String newPassword) {
    String hash = verificationTokenService.hash(rawToken);
    VerificationToken token =
        verificationTokenRepository
            .findByTokenHashAndType(hash, VerificationTokenType.PASSWORD_RESET)
            .orElseThrow(InvalidTokenException::new);

    Instant now = clock.instant();
    if (token.isConsumed()) {
      throw new InvalidTokenException();
    }
    if (token.isExpired(now)) {
      throw new TokenExpiredException();
    }

    User user = userRepository.findById(token.userId()).orElseThrow(UserNotFoundException::new);
    user.changePassword(passwordHasher.hash(newPassword), now);
    userRepository.save(user);

    refreshTokenRepository.deleteAllForUser(user.id());

    token.consume(now);
    verificationTokenRepository.save(token);
  }
}
