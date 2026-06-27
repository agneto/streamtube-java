package com.streamtube.api.web;

import com.streamtube.api.web.dto.AuthRequests.ForgotPasswordRequest;
import com.streamtube.api.web.dto.AuthRequests.LoginRequest;
import com.streamtube.api.web.dto.AuthRequests.LogoutRequest;
import com.streamtube.api.web.dto.AuthRequests.RefreshRequest;
import com.streamtube.api.web.dto.AuthRequests.RegisterRequest;
import com.streamtube.api.web.dto.AuthRequests.ResendConfirmationRequest;
import com.streamtube.api.web.dto.AuthRequests.ResetPasswordRequest;
import com.streamtube.api.web.dto.AuthResponses.MeResponse;
import com.streamtube.api.web.dto.AuthResponses.MeResponse.ChannelResponse;
import com.streamtube.api.web.dto.AuthResponses.RegisterResponse;
import com.streamtube.api.web.dto.AuthResponses.TokenResponse;
import com.streamtube.application.auth.ConfirmEmailUseCase;
import com.streamtube.application.auth.ForgotPasswordUseCase;
import com.streamtube.application.auth.GetCurrentUserUseCase;
import com.streamtube.application.auth.LoginUseCase;
import com.streamtube.application.auth.LogoutUseCase;
import com.streamtube.application.auth.RefreshTokensUseCase;
import com.streamtube.application.auth.RegisterUserUseCase;
import com.streamtube.application.auth.ResendConfirmationUseCase;
import com.streamtube.application.auth.ResetPasswordUseCase;
import com.streamtube.application.auth.result.CurrentUserView;
import com.streamtube.application.auth.result.RegisterResult;
import com.streamtube.application.auth.result.TokenPair;
import com.streamtube.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "auth", description = "Registration, login, and account management")
public class AuthController {

  private final RegisterUserUseCase registerUser;
  private final ConfirmEmailUseCase confirmEmail;
  private final ResendConfirmationUseCase resendConfirmation;
  private final LoginUseCase login;
  private final RefreshTokensUseCase refreshTokens;
  private final LogoutUseCase logout;
  private final ForgotPasswordUseCase forgotPassword;
  private final ResetPasswordUseCase resetPassword;
  private final GetCurrentUserUseCase getCurrentUser;

  public AuthController(
      RegisterUserUseCase registerUser,
      ConfirmEmailUseCase confirmEmail,
      ResendConfirmationUseCase resendConfirmation,
      LoginUseCase login,
      RefreshTokensUseCase refreshTokens,
      LogoutUseCase logout,
      ForgotPasswordUseCase forgotPassword,
      ResetPasswordUseCase resetPassword,
      GetCurrentUserUseCase getCurrentUser) {
    this.registerUser = registerUser;
    this.confirmEmail = confirmEmail;
    this.resendConfirmation = resendConfirmation;
    this.login = login;
    this.refreshTokens = refreshTokens;
    this.logout = logout;
    this.forgotPassword = forgotPassword;
    this.resetPassword = resetPassword;
    this.getCurrentUser = getCurrentUser;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a new user and auto-create their channel")
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    RegisterResult result = registerUser.execute(request.email(), request.password());
    return new RegisterResponse(result.id(), result.email());
  }

  @GetMapping("/confirm-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Confirm a user's email from a one-time token")
  public void confirmEmail(@RequestParam("token") String token) {
    confirmEmail.execute(token);
  }

  @PostMapping("/resend-confirmation")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Resend the email confirmation token")
  public void resendConfirmation(@Valid @RequestBody ResendConfirmationRequest request) {
    resendConfirmation.execute(request.email());
  }

  @PostMapping("/login")
  @Operation(summary = "Authenticate and receive access + refresh tokens")
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    return toTokenResponse(login.execute(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate a refresh token (reuse detection + grace period)")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return toTokenResponse(refreshTokens.execute(request.refreshToken()));
  }

  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Send a password reset email (always succeeds)")
  public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    forgotPassword.execute(request.email());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Reset a password from a one-time token")
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    resetPassword.execute(request.token(), request.password());
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Revoke the presented refresh token")
  public void logout(@Valid @RequestBody LogoutRequest request) {
    logout.execute(request.refreshToken());
  }

  @GetMapping("/me")
  @Operation(summary = "Current authenticated user and their channel")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
    CurrentUserView view = getCurrentUser.execute(principal.id());
    ChannelResponse channel =
        view.channel() == null
            ? null
            : new ChannelResponse(
                view.channel().id(), view.channel().nickname(), view.channel().name());
    return new MeResponse(view.id(), view.email(), view.confirmed(), channel);
  }

  private TokenResponse toTokenResponse(TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(), "Bearer", pair.expiresInSeconds(), pair.refreshToken());
  }
}
