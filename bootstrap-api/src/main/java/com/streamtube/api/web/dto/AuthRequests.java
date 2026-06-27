package com.streamtube.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request payloads for the auth endpoints. */
public final class AuthRequests {

  private AuthRequests() {}

  public record RegisterRequest(
      @Email @NotBlank String email, @NotBlank @Size(min = 8, max = 100) String password) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  public record RefreshRequest(@JsonProperty("refresh_token") @NotBlank String refreshToken) {}

  public record ForgotPasswordRequest(@Email @NotBlank String email) {}

  public record ResetPasswordRequest(
      @NotBlank String token, @NotBlank @Size(min = 8, max = 100) String password) {}

  public record ResendConfirmationRequest(@Email @NotBlank String email) {}

  public record LogoutRequest(@JsonProperty("refresh_token") @NotBlank String refreshToken) {}
}
