package com.streamtube.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitingFilterTest {

  private RateLimitingFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RateLimitingFilter(new ObjectMapper().findAndRegisterModules());
  }

  private MockHttpServletResponse request(String method, String uri, String ip)
      throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRequestURI(uri);
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private MockHttpServletResponse exhaustAndRepeat(String method, String uri, String ip)
      throws ServletException, IOException {
    for (int i = 0; i < 10; i++) {
      assertThat(request(method, uri, ip).getStatus()).isEqualTo(200);
    }
    return request(method, uri, ip);
  }

  @Test
  void returns429WithRetryAfterAndErrorEnvelopeWhenLimitExceeded() throws Exception {
    MockHttpServletResponse rejected = exhaustAndRepeat("POST", "/api/v1/auth/login", "10.0.0.1");

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
    assertThat(rejected.getContentType()).isEqualTo("application/json");
    var body = new ObjectMapper().readTree(rejected.getContentAsString());
    assertThat(body.get("statusCode").asInt()).isEqualTo(429);
    assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
    assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/login");
  }

  @Test
  void limitsResetPasswordAndConfirmEmail() throws Exception {
    assertThat(exhaustAndRepeat("POST", "/api/v1/auth/reset-password", "10.0.0.2").getStatus())
        .isEqualTo(429);
    assertThat(exhaustAndRepeat("GET", "/api/v1/auth/confirm-email", "10.0.0.2").getStatus())
        .isEqualTo(429);
  }

  @Test
  void ipsHaveIndependentBuckets() throws Exception {
    assertThat(exhaustAndRepeat("POST", "/api/v1/auth/login", "10.0.0.3").getStatus()).isEqualTo(429);
    assertThat(request("POST", "/api/v1/auth/login", "10.0.0.4").getStatus()).isEqualTo(200);
  }

  @Test
  void unlimitedPathsAreNeverThrottled() throws Exception {
    for (int i = 0; i < 30; i++) {
      assertThat(request("GET", "/api/v1/videos/some-slug", "10.0.0.5").getStatus()).isEqualTo(200);
      assertThat(request("POST", "/api/v1/videos", "10.0.0.5").getStatus()).isEqualTo(200);
    }
  }
}
