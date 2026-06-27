package com.streamtube.domain.shared;

/**
 * Base type for all domain/business exceptions. Framework-free: the web layer maps these to
 * HTTP responses via a {@code @RestControllerAdvice}; the domain itself knows nothing about HTTP.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
