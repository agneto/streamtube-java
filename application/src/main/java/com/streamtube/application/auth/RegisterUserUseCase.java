package com.streamtube.application.auth;

import com.streamtube.application.auth.result.RegisterResult;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.NicknameGenerator;
import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.VerificationTokenService;
import com.streamtube.application.port.out.VerificationTokenService.IssuedVerificationToken;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.AuthExceptions.EmailAlreadyRegisteredException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a user, auto-creates their channel, and sends an email confirmation token. */
@Service
public class RegisterUserUseCase {

  private static final int MAX_NICKNAME_ATTEMPTS = 5;

  private final UserRepository userRepository;
  private final ChannelRepository channelRepository;
  private final VerificationTokenRepository verificationTokenRepository;
  private final PasswordHasher passwordHasher;
  private final VerificationTokenService verificationTokenService;
  private final NicknameGenerator nicknameGenerator;
  private final MailSender mailSender;
  private final Clock clock;

  public RegisterUserUseCase(
      UserRepository userRepository,
      ChannelRepository channelRepository,
      VerificationTokenRepository verificationTokenRepository,
      PasswordHasher passwordHasher,
      VerificationTokenService verificationTokenService,
      NicknameGenerator nicknameGenerator,
      MailSender mailSender,
      Clock clock) {
    this.userRepository = userRepository;
    this.channelRepository = channelRepository;
    this.verificationTokenRepository = verificationTokenRepository;
    this.passwordHasher = passwordHasher;
    this.verificationTokenService = verificationTokenService;
    this.nicknameGenerator = nicknameGenerator;
    this.mailSender = mailSender;
    this.clock = clock;
  }

  @Transactional
  public RegisterResult execute(String email, String rawPassword) {
    String normalizedEmail = email.trim().toLowerCase();
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyRegisteredException();
    }

    Instant now = clock.instant();
    User user =
        userRepository.save(
            User.register(UUID.randomUUID(), normalizedEmail, passwordHasher.hash(rawPassword), now));

    String nickname = generateUniqueNickname(normalizedEmail);
    String name = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
    channelRepository.save(
        Channel.createForUser(UUID.randomUUID(), user.id(), name, nickname, now));

    IssuedVerificationToken token = verificationTokenService.issueConfirmation();
    verificationTokenRepository.save(
        VerificationToken.issue(
            UUID.randomUUID(),
            user.id(),
            VerificationTokenType.EMAIL_CONFIRMATION,
            token.tokenHash(),
            token.expiresAt(),
            now));

    mailSender.sendConfirmationEmail(user.email(), token.rawValue());
    return new RegisterResult(user.id(), user.email());
  }

  private String generateUniqueNickname(String email) {
    for (int attempt = 0; attempt < MAX_NICKNAME_ATTEMPTS; attempt++) {
      String candidate = nicknameGenerator.generate(email);
      if (!channelRepository.existsByNickname(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("Could not generate a unique nickname after retries");
  }
}
