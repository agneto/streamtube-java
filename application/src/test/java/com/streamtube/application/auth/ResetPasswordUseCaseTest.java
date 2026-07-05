package com.streamtube.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamtube.application.port.out.PasswordHasher;
import com.streamtube.application.port.out.VerificationTokenService;
import com.streamtube.domain.auth.RefreshTokenRepository;
import com.streamtube.domain.auth.VerificationToken;
import com.streamtube.domain.auth.VerificationTokenRepository;
import com.streamtube.domain.auth.VerificationTokenType;
import com.streamtube.domain.shared.AuthExceptions.InvalidTokenException;
import com.streamtube.domain.shared.AuthExceptions.TokenExpiredException;
import com.streamtube.domain.user.User;
import com.streamtube.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ResetPasswordUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private static final String RAW = "raw-token";
  private static final String HASH = "hash";

  private VerificationTokenRepository verificationTokens;
  private VerificationTokenService verificationTokenService;
  private UserRepository users;
  private RefreshTokenRepository refreshTokens;
  private PasswordHasher passwordHasher;
  private ResetPasswordUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    verificationTokens = Mockito.mock(VerificationTokenRepository.class);
    verificationTokenService = Mockito.mock(VerificationTokenService.class);
    users = Mockito.mock(UserRepository.class);
    refreshTokens = Mockito.mock(RefreshTokenRepository.class);
    passwordHasher = Mockito.mock(PasswordHasher.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    useCase =
        new ResetPasswordUseCase(
            verificationTokens, verificationTokenService, users, refreshTokens, passwordHasher, clock);

    userId = UUID.randomUUID();
    when(verificationTokenService.hash(RAW)).thenReturn(HASH);
    when(passwordHasher.hash("new-password")).thenReturn("new-hash");
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "u@test.com", "old-hash", true, NOW, NOW)));
  }

  private VerificationToken token(Instant expiresAt, Instant consumedAt) {
    return new VerificationToken(
        UUID.randomUUID(),
        userId,
        VerificationTokenType.PASSWORD_RESET,
        HASH,
        expiresAt,
        consumedAt,
        NOW);
  }

  @Test
  void resetsPasswordAndDeletesAllRefreshTokens() {
    when(verificationTokens.findByTokenHashAndType(HASH, VerificationTokenType.PASSWORD_RESET))
        .thenReturn(Optional.of(token(NOW.plusSeconds(3600), null)));

    useCase.execute(RAW, "new-password");

    ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
    verify(users).save(savedUser.capture());
    assertThat(savedUser.getValue().passwordHash()).isEqualTo("new-hash");
    verify(refreshTokens).deleteAllForUser(userId);

    ArgumentCaptor<VerificationToken> savedToken =
        ArgumentCaptor.forClass(VerificationToken.class);
    verify(verificationTokens).save(savedToken.capture());
    assertThat(savedToken.getValue().isConsumed()).isTrue();
  }

  @Test
  void throwsOnUnknownToken() {
    when(verificationTokens.findByTokenHashAndType(HASH, VerificationTokenType.PASSWORD_RESET))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(RAW, "new-password"))
        .isInstanceOf(InvalidTokenException.class);
    verify(refreshTokens, never()).deleteAllForUser(any());
  }

  @Test
  void throwsOnConsumedToken() {
    when(verificationTokens.findByTokenHashAndType(HASH, VerificationTokenType.PASSWORD_RESET))
        .thenReturn(Optional.of(token(NOW.plusSeconds(3600), NOW.minusSeconds(60))));

    assertThatThrownBy(() -> useCase.execute(RAW, "new-password"))
        .isInstanceOf(InvalidTokenException.class);
    verify(users, never()).save(any());
    verify(refreshTokens, never()).deleteAllForUser(any());
  }

  @Test
  void throwsOnExpiredToken() {
    when(verificationTokens.findByTokenHashAndType(HASH, VerificationTokenType.PASSWORD_RESET))
        .thenReturn(Optional.of(token(NOW.minusSeconds(1), null)));

    assertThatThrownBy(() -> useCase.execute(RAW, "new-password"))
        .isInstanceOf(TokenExpiredException.class);
    verify(users, never()).save(any());
    verify(refreshTokens, never()).deleteAllForUser(any());
  }
}
