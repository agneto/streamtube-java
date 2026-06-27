package com.streamtube.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class RefreshTokensUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
  private static final String RAW = "raw-token";
  private static final String HASH = "hash";

  private RefreshTokenRepository refreshTokens;
  private UserRepository users;
  private AccessTokenService accessTokens;
  private RefreshTokenService refreshTokenService;
  private RefreshTokensUseCase useCase;
  private UUID userId;
  private UUID family;

  @BeforeEach
  void setUp() {
    refreshTokens = Mockito.mock(RefreshTokenRepository.class);
    users = Mockito.mock(UserRepository.class);
    accessTokens = Mockito.mock(AccessTokenService.class);
    refreshTokenService = Mockito.mock(RefreshTokenService.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    useCase = new RefreshTokensUseCase(refreshTokens, users, accessTokens, refreshTokenService, clock);

    userId = UUID.randomUUID();
    family = UUID.randomUUID();
    when(refreshTokenService.hash(RAW)).thenReturn(HASH);
    when(refreshTokenService.issue(any(), any(), any()))
        .thenReturn(new IssuedRefreshToken("new-raw", "new-hash", NOW.plusSeconds(3600)));
    when(accessTokens.issue(any(), any())).thenReturn(new IssuedAccessToken("access", 900));
    when(users.findById(userId))
        .thenReturn(
            Optional.of(new User(userId, "u@test.com", "pw", true, NOW, NOW)));
  }

  private RefreshToken token(Instant expiresAt, Instant revokedAt) {
    return new RefreshToken(
        UUID.randomUUID(), userId, family, UUID.randomUUID(), HASH, expiresAt, revokedAt, NOW);
  }

  @Test
  void rotatesValidToken() {
    when(refreshTokens.findByTokenHash(HASH))
        .thenReturn(Optional.of(token(NOW.plusSeconds(3600), null)));

    TokenPair pair = useCase.execute(RAW);

    assertThat(pair.accessToken()).isEqualTo("access");
    assertThat(pair.refreshToken()).isEqualTo("new-raw");
    // current revoked (save) + new token saved
    verify(refreshTokens, times(2)).save(any());
    verify(refreshTokens, never()).revokeFamily(any(), any());
  }

  @Test
  void throwsOnUnknownToken() {
    when(refreshTokens.findByTokenHash(HASH)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(RAW)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void throwsOnExpiredToken() {
    when(refreshTokens.findByTokenHash(HASH))
        .thenReturn(Optional.of(token(NOW.minusSeconds(1), null)));
    assertThatThrownBy(() -> useCase.execute(RAW)).isInstanceOf(TokenExpiredException.class);
  }

  @Test
  void detectsReusePastGraceAndRevokesFamily() {
    when(refreshTokens.findByTokenHash(HASH))
        .thenReturn(Optional.of(token(NOW.plusSeconds(3600), NOW.minusSeconds(20))));

    assertThatThrownBy(() -> useCase.execute(RAW))
        .isInstanceOf(TokenReuseDetectedException.class);
    verify(refreshTokens).revokeFamily(eq(family), any());
  }

  @Test
  void toleratesRevokedWithinGracePeriod() {
    when(refreshTokens.findByTokenHash(HASH))
        .thenReturn(Optional.of(token(NOW.plusSeconds(3600), NOW.minusSeconds(5))));

    TokenPair pair = useCase.execute(RAW);

    assertThat(pair.refreshToken()).isEqualTo("new-raw");
    verify(refreshTokens, never()).revokeFamily(any(), any());
  }
}
