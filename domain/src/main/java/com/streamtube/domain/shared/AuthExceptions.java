package com.streamtube.domain.shared;

/** Domain exceptions for the auth/account context. Each carries a stable machine code. */
public final class AuthExceptions {

  private AuthExceptions() {}

  public static final class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException() {
      super("EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }
  }

  public static final class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
      super("INVALID_CREDENTIALS", "Invalid email or password");
    }
  }

  public static final class EmailNotConfirmedException extends DomainException {
    public EmailNotConfirmedException() {
      super("EMAIL_NOT_CONFIRMED", "Email address has not been confirmed");
    }
  }

  public static final class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
      super("INVALID_TOKEN", "Token is invalid");
    }
  }

  public static final class TokenExpiredException extends DomainException {
    public TokenExpiredException() {
      super("TOKEN_EXPIRED", "Token has expired");
    }
  }

  public static final class TokenReuseDetectedException extends DomainException {
    public TokenReuseDetectedException() {
      super("TOKEN_REUSE_DETECTED", "Refresh token reuse detected; session revoked");
    }
  }

  public static final class UserNotFoundException extends DomainException {
    public UserNotFoundException() {
      super("USER_NOT_FOUND", "User not found");
    }
  }
}
