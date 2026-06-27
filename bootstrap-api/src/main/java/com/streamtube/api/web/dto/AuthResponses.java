package com.streamtube.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Response payloads for the auth endpoints. */
public final class AuthResponses {

  private AuthResponses() {}

  public record RegisterResponse(UUID id, String email) {}

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn,
      @JsonProperty("refresh_token") String refreshToken) {}

  public record MeResponse(UUID id, String email, boolean confirmed, ChannelResponse channel) {

    public record ChannelResponse(UUID id, String nickname, String name) {}
  }
}
