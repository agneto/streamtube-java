package com.streamtube.api.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProdSecretsGuardTest {

  private static final String DEV_SECRET = "dev-only-insecure-secret-change-me-0123456789abcdef";

  private final ProdSecretsGuard guard = new ProdSecretsGuard();

  private MockEnvironment env(String profile, String secret) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profile);
    env.setProperty("auth.jwt.secret", secret);
    return env;
  }

  @Test
  void rejectsCommittedDevSecretInProd() {
    assertThatThrownBy(() -> guard.postProcessEnvironment(env("prod", DEV_SECRET), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void acceptsRealSecretInProd() {
    assertThatCode(
            () ->
                guard.postProcessEnvironment(
                    env("prod", "a-genuinely-configured-production-secret-value"), null))
        .doesNotThrowAnyException();
  }

  @Test
  void ignoresDevSecretOutsideProd() {
    assertThatCode(() -> guard.postProcessEnvironment(env("dev", DEV_SECRET), null))
        .doesNotThrowAnyException();
  }
}
