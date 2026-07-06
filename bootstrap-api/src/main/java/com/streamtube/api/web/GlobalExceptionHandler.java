package com.streamtube.api.web;

import com.streamtube.api.web.dto.ErrorEnvelope;
import com.streamtube.domain.shared.DomainErrorType;
import com.streamtube.domain.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception that reaches the MVC layer to the shared {@link ErrorEnvelope}, including a
 * catch-all so unexpected errors never leak Spring's default error shape. Security failures raised
 * in the filter chain (401/403) are handled by {@code SecurityErrorResponses} with the same
 * envelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorEnvelope> handleDomain(
      DomainException ex, HttpServletRequest request) {
    HttpStatus status = statusFor(ex.type());
    return ResponseEntity.status(status).body(envelope(status, ex.code(), ex.getMessage(), request));
  }

  /**
   * Safety net for unique-constraint races (e.g. two simultaneous registrations passing the
   * exists-check): a conflict must never surface as a 500.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorEnvelope> handleDataIntegrity(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            envelope(
                HttpStatus.CONFLICT, "CONFLICT", "Resource already exists or conflicts", request));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorEnvelope> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .orElse("Validation failed");
    return ResponseEntity.badRequest()
        .body(envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message, request));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorEnvelope> handleUnreadableBody(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            envelope(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Request body is missing or malformed",
                request));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorEnvelope> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            envelope(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                ex.getName() + " has an invalid value",
                request));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorEnvelope> handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            envelope(
                HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                ex.getParameterName() + " is required",
                request));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            envelope(
                HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", request));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorEnvelope> handleNoResource(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(envelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request));
  }

  /** Method-security denials thrown inside controllers (none today, but must not become 500s). */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorEnvelope> handleAccessDenied(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(envelope(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            envelope(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request));
  }

  /** Exhaustive by construction: a new DomainErrorType fails compilation until mapped here. */
  private HttpStatus statusFor(DomainErrorType type) {
    return switch (type) {
      case VALIDATION -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case GONE -> HttpStatus.GONE;
      case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
  }

  private ErrorEnvelope envelope(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return new ErrorEnvelope(
        status.value(), code, message, Instant.now(), request.getRequestURI());
  }
}
