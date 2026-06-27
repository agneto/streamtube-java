package com.streamtube.api.web;

import com.streamtube.api.web.dto.ErrorEnvelope;
import com.streamtube.domain.shared.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and validation exceptions to the shared error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
