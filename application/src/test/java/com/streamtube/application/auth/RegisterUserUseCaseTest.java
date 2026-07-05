package com.streamtube.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.auth.result.RegisterResult;
import com.streamtube.application.port.out.MailSender;
import com.streamtube.application.port.out.NicknameGenerator;
import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.VerificationTokenService;
import com.streamtube.application.port.out.VerificationTokenService.IssuedVerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.AuthExceptions.EmailAlreadyRegisteredException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RegisterUserUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

  private UserRepository users;
  private ChannelRepository channels;
  private VerificationTokenRepository verificationTokens;
  private PasswordHasher passwordHasher;
  private VerificationTokenService verificationTokenService;
  private NicknameGenerator nicknameGenerator;
  private MailSender mailSender;
  private RegisterUserUseCase useCase;

  @BeforeEach
  void setUp() {
    users = Mockito.mock(UserRepository.class);
    channels = Mockito.mock(ChannelRepository.class);
    verificationTokens = Mockito.mock(VerificationTokenRepository.class);
    passwordHasher = Mockito.mock(PasswordHasher.class);
    verificationTokenService = Mockito.mock(VerificationTokenService.class);
    nicknameGenerator = Mockito.mock(NicknameGenerator.class);
    mailSender = Mockito.mock(MailSender.class);
    useCase =
        new RegisterUserUseCase(
            users,
            channels,
            verificationTokens,
            passwordHasher,
            verificationTokenService,
            nicknameGenerator,
            mailSender,
            Clock.fixed(NOW, ZoneOffset.UTC));

    when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(channels.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(passwordHasher.hash("password")).thenReturn("hashed");
    when(nicknameGenerator.generate(anyString())).thenReturn("nick");
    when(verificationTokenService.issueConfirmation())
        .thenReturn(new IssuedVerificationToken("raw-token", "token-hash", NOW.plusSeconds(86400)));
  }

  @Test
  void registersUserWithChannelTokenAndConfirmationMail() {
    when(users.existsByEmail("user@test.com")).thenReturn(false);
    when(channels.existsByNickname("nick")).thenReturn(false);

    RegisterResult result = useCase.execute("  User@Test.COM ", "password");

    assertThat(result.email()).isEqualTo("user@test.com");

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(users).save(savedUser.capture());
    assertThat(savedUser.getValue().email()).isEqualTo("user@test.com");
    assertThat(savedUser.getValue().passwordHash()).isEqualTo("hashed");
    assertThat(savedUser.getValue().isConfirmed()).isFalse();

    ArgumentCaptor<Channel> savedChannel = ArgumentCaptor.forClass(Channel.class);
    verify(channels).save(savedChannel.capture());
    assertThat(savedChannel.getValue().userId()).isEqualTo(savedUser.getValue().id());
    assertThat(savedChannel.getValue().name()).isEqualTo("user");
    assertThat(savedChannel.getValue().nickname()).isEqualTo("nick");

    verify(verificationTokens).save(any());
    verify(mailSender).sendConfirmationEmail(eq("user@test.com"), eq("raw-token"));
  }

  @Test
  void rejectsDuplicateEmail() {
    when(users.existsByEmail("dup@test.com")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute("dup@test.com", "password"))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(users, never()).save(any());
    verify(mailSender, never()).sendConfirmationEmail(any(), any());
  }

  @Test
  void retriesNicknameGenerationOnCollision() {
    when(users.existsByEmail("user@test.com")).thenReturn(false);
    when(nicknameGenerator.generate(anyString())).thenReturn("taken", "free");
    when(channels.existsByNickname("taken")).thenReturn(true);
    when(channels.existsByNickname("free")).thenReturn(false);

    useCase.execute("user@test.com", "password");

    ArgumentCaptor<Channel> savedChannel = ArgumentCaptor.forClass(Channel.class);
    verify(channels).save(savedChannel.capture());
    assertThat(savedChannel.getValue().nickname()).isEqualTo("free");
  }

  @Test
  void failsAfterExhaustingNicknameAttempts() {
    when(users.existsByEmail("user@test.com")).thenReturn(false);
    when(channels.existsByNickname(anyString())).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute("user@test.com", "password"))
        .isInstanceOf(IllegalStateException.class);
    verify(channels, never()).save(any());
  }
}
