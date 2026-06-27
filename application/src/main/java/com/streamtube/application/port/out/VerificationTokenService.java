package com.streamtube.application.port.out;

import java.time.Instant;

/** Output port that mints opaque verification-token secrets (email confirm / password reset). */
public interface VerificationTokenService {

  IssuedVerificationToken issueConfirmation();

  IssuedVerificationToken issuePasswordReset();

  String hash(String rawToken);

  record IssuedVerificationToken(String rawValue, String tokenHash, Instant expiresAt) {}
}
