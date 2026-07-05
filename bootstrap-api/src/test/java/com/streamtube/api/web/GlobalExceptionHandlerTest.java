package com.streamtube.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.streamtube.api.web.dto.ErrorEnvelope;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some/path");

  @Test
  void unexpectedExceptionBecomes500EnvelopeWithoutLeakingDetails() {
    ResponseEntity<ErrorEnvelope> response =
        handler.handleUnexpected(new IllegalStateException("internal detail"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).doesNotContain("internal detail");
    assertThat(response.getBody().path()).isEqualTo("/some/path");
  }

  @Test
  void typeMismatchBecomes400WithParameterName() {
    MethodArgumentTypeMismatchException ex =
        Mockito.mock(MethodArgumentTypeMismatchException.class);
    when(ex.getName()).thenReturn("id");

    ResponseEntity<ErrorEnvelope> response = handler.handleTypeMismatch(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
    assertThat(response.getBody().message()).contains("id");
  }

  @Test
  void methodNotSupportedBecomes405Envelope() {
    ResponseEntity<ErrorEnvelope> response = handler.handleMethodNotSupported(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
  }

  @Test
  void unreadableBodyBecomes400Envelope() {
    ResponseEntity<ErrorEnvelope> response = handler.handleUnreadableBody(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
  }
}
