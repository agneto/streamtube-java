package com.streamtube.application.port.out;

import java.util.UUID;

/** Output port that issues stateless JWT access tokens (implemented with jjwt in infrastructure). */
public interface AccessTokenService {

  IssuedAccessToken issue(UUID userId, String email);

  record IssuedAccessToken(String token, long expiresInSeconds) {}
}
