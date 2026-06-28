package com.streamtube.domain.auth;

import java.util.Optional;

/** Output port for verification-token persistence. */
public interface VerificationTokenRepository {

  VerificationToken save(VerificationToken token);

  Optional<VerificationToken> findByTokenHashAndType(String tokenHash, VerificationTokenType type);
}
