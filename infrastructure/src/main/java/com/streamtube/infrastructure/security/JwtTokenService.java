package com.streamtube.infrastructure.security;

import com.streamtube.application.port.out.AccessTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues and verifies stateless HS256 JWT access tokens (jjwt). */
@Component
public class JwtTokenService implements AccessTokenService {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

  private final SecretKey key;
  private final long accessTtlSeconds;

  public JwtTokenService(
      @Value("${auth.jwt.secret}") String secret,
      @Value("${auth.jwt.access-ttl-seconds}") long accessTtlSeconds) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTtlSeconds = accessTtlSeconds;
  }

  @Override
  public IssuedAccessToken issue(UUID userId, String email) {
    Instant now = Instant.now();
    Instant expiry = now.plusSeconds(accessTtlSeconds);
    String token =
        Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(key)
            .compact();
    return new IssuedAccessToken(token, accessTtlSeconds);
  }

  /** Verifies signature + expiry and extracts the principal; empty if the token is invalid. */
  public Optional<AuthenticatedUser> verify(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      UUID userId = UUID.fromString(claims.getSubject());
      String email = claims.get("email", String.class);
      return Optional.of(new AuthenticatedUser(userId, email));
    } catch (RuntimeException e) {
      // Debug-level: expired vs. bad-signature matters when investigating auth incidents,
      // but must not leak to clients nor flood production logs.
      log.debug("JWT verification failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
      return Optional.empty();
    }
  }
}
