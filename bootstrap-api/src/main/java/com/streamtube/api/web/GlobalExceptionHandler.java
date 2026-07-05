package com.streamtube.api.web;

import com.streamtube.api.web.dto.ErrorEnvelope;
import com.streamtube.domain.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    HttpStatus status = statusFor(ex.code());
    return ResponseEntity.status(status).body(envelope(status, ex.code(), ex.getMessage(), request));
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

  private HttpStatus statusFor(String code) {
    return switch (code) {
      case "EMAIL_ALREADY_REGISTERED" -> HttpStatus.CONFLICT;
      case "INVALID_CREDENTIALS", "TOKEN_REUSE_DETECTED" -> HttpStatus.UNAUTHORIZED;
      case "EMAIL_NOT_CONFIRMED", "FORBIDDEN_VIDEO_ACCESS" -> HttpStatus.FORBIDDEN;
      case "TOKEN_EXPIRED" -> HttpStatus.GONE;
      case "USER_NOT_FOUND", "VIDEO_NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "UPLOAD_NOT_COMPLETED" -> HttpStatus.CONFLICT;
      case "VIDEO_STATUS_CONFLICT", "VIDEO_NOT_READY" -> HttpStatus.UNPROCESSABLE_ENTITY;
      default -> HttpStatus.BAD_REQUEST;
    };
  }

  private ErrorEnvelope envelope(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return new ErrorEnvelope(
        status.value(), code, message, Instant.now(), request.getRequestURI());
  }
}
