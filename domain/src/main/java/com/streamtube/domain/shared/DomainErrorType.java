package com.streamtube.domain.shared;

/**
 * Abstract category of a domain error. The web layer maps each category to an HTTP status
 * mechanically (exhaustive switch), so a new exception can never fall through to a generic 400
 * by someone forgetting to update the handler. Framework/HTTP-free on purpose.
 */
public enum DomainErrorType {
  VALIDATION,
  NOT_FOUND,
  CONFLICT,
  FORBIDDEN,
  UNAUTHORIZED,
  GONE,
  UNPROCESSABLE
}
