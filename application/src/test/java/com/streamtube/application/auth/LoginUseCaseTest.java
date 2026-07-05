package com.streamtube.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.auth.result.TokenPair;
import com.streamtube.application.port.out.AccessTokenService;
import com.streamtube.application.port.out.AccessTokenService.IssuedAccessToken;
import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.RefreshTokenService;
import com.streamtube.application.port.out.RefreshTokenService.IssuedRefreshToken;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.domain.shared.AuthExceptions.EmailNotConfirmedException;
import com.streamtube.domain.shared.AuthExceptions.InvalidCredentialsException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LoginUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private static final String EMAIL = "user@test.com";

  private UserRepository users;
  private RefreshTokenRepository refreshTokens;
  private PasswordHasher passwordHasher;
  private AccessTokenService accessTokens;
  private RefreshTokenService refreshTokenService;
  private LoginUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    users = Mockito.mock(UserRepository.class);
    refreshTokens = Mockito.mock(RefreshTokenRepository.class);
    passwordHasher = Mockito.mock(PasswordHasher.class);
    accessTokens = Mockito.mock(AccessTokenService.class);
    refreshTokenService = Mockito.mock(RefreshTokenService.class);
    useCase =
        new LoginUseCase(
            users,
            refreshTokens,
            passwordHasher,
            accessTokens,
            refreshTokenService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    userId = UUID.randomUUID();
    when(refreshTokenService.issue(any(), any(), any()))
        .thenReturn(new IssuedRefreshToken("refresh-raw", "refresh-hash", NOW.plusSeconds(3600)));
    when(accessTokens.issue(any(), any())).thenReturn(new IssuedAccessToken("access", 900));
  }

  private User user(boolean confirmed) {
    return new User(userId, EMAIL, "stored-hash", confirmed, NOW, NOW);
  }

  @Test
  void issuesTokenPairForValidCredentials() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
    when(passwordHasher.matches("password", "stored-hash")).thenReturn(true);

    TokenPair pair = useCase.execute(EMAIL, "password");

    assertThat(pair.accessToken()).isEqualTo("access");
    assertThat(pair.refreshToken()).isEqualTo("refresh-raw");
    verify(refreshTokens).save(any());
  }

  @Test
  void normalizesEmailBeforeLookup() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
    when(passwordHasher.matches("password", "stored-hash")).thenReturn(true);

    useCase.execute("  User@Test.COM ", "password");

    verify(users).findByEmail(EMAIL);
  }

  @Test
  void rejectsUnknownEmail() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(EMAIL, "password"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void rejectsWrongPassword() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
    when(passwordHasher.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(EMAIL, "wrong"))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(refreshTokens, never()).save(any());
  }

  @Test
  void rejectsUnconfirmedEmailEvenWithCorrectPassword() {
    when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
    when(passwordHasher.matches("password", "stored-hash")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(EMAIL, "password"))
        .isInstanceOf(EmailNotConfirmedException.class);
    verify(refreshTokens, never()).save(any());
  }
}
