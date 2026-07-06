package com.streamtube.domain.shared;

/**
 * Base type for all domain/business exceptions. Framework-free: the web layer maps these to
 * HTTP responses via a {@code @RestControllerAdvice}; the domain itself knows nothing about HTTP.
 * Each exception carries a stable machine {@code code} and an abstract {@link DomainErrorType}
 * category that the web layer translates to a status.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;
  private final DomainErrorType type;

  protected DomainException(String code, String message, DomainErrorType type) {
    super(message);
    this.code = code;
    this.type = type;
  }

  public String code() {
    return code;
  }

  public DomainErrorType type() {
    return type;
  }
}
