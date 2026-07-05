package com.streamtube.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.api.web.dto.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes security failures (401/403 raised in the filter chain, which never reach the controller
 * advice) as the shared {@link ErrorEnvelope}, so every error the API emits has the same shape.
 */
@Component
public class SecurityErrorResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public SecurityErrorResponses(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                new ErrorEnvelope(
                    status.value(), code, message, Instant.now(), request.getRequestURI())));
  }
}
