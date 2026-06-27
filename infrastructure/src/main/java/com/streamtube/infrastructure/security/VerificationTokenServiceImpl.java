package com.streamtube.infrastructure.security;

import com.streamtube.application.port.out.VerificationTokenService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Issues opaque verification-token secrets (email confirmation / password reset). */
@Component
public class VerificationTokenServiceImpl implements VerificationTokenService {

  private final long confirmTtlSeconds;
  private final long resetTtlSeconds;
  private final Clock clock;

  public VerificationTokenServiceImpl(
      @Value("${auth.email-confirm.ttl-seconds}") long confirmTtlSeconds,
      @Value("${auth.password-reset.ttl-seconds}") long resetTtlSeconds,
      Clock clock) {
    this.confirmTtlSeconds = confirmTtlSeconds;
    this.resetTtlSeconds = resetTtlSeconds;
    this.clock = clock;
  }

  @Override
  public IssuedVerificationToken issueConfirmation() {
    return issue(confirmTtlSeconds);
  }

  @Override
  public IssuedVerificationToken issuePasswordReset() {
    return issue(resetTtlSeconds);
  }

  @Override
  public String hash(String rawToken) {
    return SecureTokens.sha256Hex(rawToken);
  }

  private IssuedVerificationToken issue(long ttlSeconds) {
    String raw = SecureTokens.randomRawValue();
    return new IssuedVerificationToken(
        raw, SecureTokens.sha256Hex(raw), clock.instant().plusSeconds(ttlSeconds));
  }
}
