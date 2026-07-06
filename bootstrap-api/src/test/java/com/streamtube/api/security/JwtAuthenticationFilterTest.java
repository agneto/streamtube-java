package com.streamtube.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamtube.infrastructure.security.AuthenticatedUser;
import com.streamtube.infrastructure.security.JwtTokenService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET = "test-secret-0123456789abcdef-0123456789abcdef";

  private JwtTokenService tokenService;
  private JwtAuthenticationFilter filter;
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    tokenService = new JwtTokenService(SECRET, 900);
    filter = new JwtAuthenticationFilter(tokenService);
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
  }

  private Authentication run(String authorizationHeader) throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
    if (authorizationHeader != null) {
      request.addHeader("Authorization", authorizationHeader);
    }
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    return SecurityContextHolder.getContext().getAuthentication();
  }

  @Test
  void validTokenPopulatesSecurityContext() throws Exception {
    String token = tokenService.issue(userId, "user@test.com").token();

    Authentication auth = run("Bearer " + token);

    assertThat(auth).isNotNull();
    AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
    assertThat(principal.id()).isEqualTo(userId);
    assertThat(principal.email()).isEqualTo("user@test.com");
  }

  @Test
  void tamperedTokenLeavesContextEmpty() throws Exception {
    String token = tokenService.issue(userId, "user@test.com").token();

    assertThat(run("Bearer " + token + "x")).isNull();
  }

  @Test
  void expiredTokenLeavesContextEmpty() throws Exception {
    String expired = new JwtTokenService(SECRET, -60).issue(userId, "user@test.com").token();

    assertThat(run("Bearer " + expired)).isNull();
  }

  @Test
  void tokenSignedWithAnotherSecretLeavesContextEmpty() throws Exception {
    String foreign =
        new JwtTokenService("another-secret-0123456789abcdef-0123456789ab", 900)
            .issue(userId, "user@test.com")
            .token();

    assertThat(run("Bearer " + foreign)).isNull();
  }

  @Test
  void missingHeaderLeavesContextEmpty() throws Exception {
    assertThat(run(null)).isNull();
  }
}
