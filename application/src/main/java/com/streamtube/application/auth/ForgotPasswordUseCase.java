package com.streamtube.application.auth;

import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.VerificationTokenService;
import com.streamtube.application.port.out.VerificationTokenService.IssuedVerificationToken;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a password-reset token and emails it. Always succeeds silently to avoid user enumeration:
 * unknown emails produce no output.
 */
@Service
public class ForgotPasswordUseCase {

  private final UserRepository userRepository;
  private final VerificationTokenRepository verificationTokenRepository;
  private final VerificationTokenService verificationTokenService;
  private final MailSender mailSender;
  private final Clock clock;

  public ForgotPasswordUseCase(
      UserRepository userRepository,
      VerificationTokenRepository verificationTokenRepository,
      VerificationTokenService verificationTokenService,
      MailSender mailSender,
      Clock clock) {
    this.userRepository = userRepository;
    this.verificationTokenRepository = verificationTokenRepository;
    this.verificationTokenService = verificationTokenService;
    this.mailSender = mailSender;
    this.clock = clock;
  }

  @Transactional
  public void execute(String email) {
    Optional<User> maybeUser = userRepository.findByEmail(email.trim().toLowerCase());
    if (maybeUser.isEmpty()) {
      return;
    }
    User user = maybeUser.get();
    Instant now = clock.instant();
    IssuedVerificationToken token = verificationTokenService.issuePasswordReset();
    verificationTokenRepository.save(
        VerificationToken.issue(
            UUID.randomUUID(),
            user.id(),
            VerificationTokenType.PASSWORD_RESET,
            token.tokenHash(),
            token.expiresAt(),
            now));
    mailSender.sendPasswordResetEmail(user.email(), token.rawValue());
  }
}
