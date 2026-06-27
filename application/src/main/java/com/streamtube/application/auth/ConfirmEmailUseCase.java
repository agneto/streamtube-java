package com.streamtube.application.auth;

import com.streamtube.application.port.out.VerificationTokenService;
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

/** Confirms a user's email from a one-time confirmation token. */
@Service
public class ConfirmEmailUseCase {

  private final VerificationTokenRepository verificationTokenRepository;
  private final VerificationTokenService verificationTokenService;
  private final UserRepository userRepository;
  private final Clock clock;

  public ConfirmEmailUseCase(
      VerificationTokenRepository verificationTokenRepository,
      VerificationTokenService verificationTokenService,
      UserRepository userRepository,
      Clock clock) {
    this.verificationTokenRepository = verificationTokenRepository;
    this.verificationTokenService = verificationTokenService;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Transactional
  public void execute(String rawToken) {
    String hash = verificationTokenService.hash(rawToken);
    VerificationToken token =
        verificationTokenRepository
            .findByTokenHashAndType(hash, VerificationTokenType.EMAIL_CONFIRMATION)
            .orElseThrow(InvalidTokenException::new);

    Instant now = clock.instant();
    if (token.isConsumed()) {
      throw new InvalidTokenException();
    }
    if (token.isExpired(now)) {
      throw new TokenExpiredException();
    }

    User user = userRepository.findById(token.userId()).orElseThrow(UserNotFoundException::new);
    user.confirm(now);
    userRepository.save(user);

    token.consume(now);
    verificationTokenRepository.save(token);
  }
}
