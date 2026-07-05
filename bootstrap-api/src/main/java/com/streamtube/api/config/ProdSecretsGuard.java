package com.streamtube.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Fails startup when the prod profile runs with the well-known dev JWT secret. The prod profile
 * already has no fallback for {@code JWT_SECRET} (missing var aborts on placeholder resolution);
 * this guard covers the remaining hole of the variable being set to the committed dev value.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} (registered in {@code META-INF/spring.factories})
 * so it aborts before any bean is created — in particular before Flyway/Hikari touch the database.
 */
public class ProdSecretsGuard implements EnvironmentPostProcessor {

  static final String DEV_SECRET_MARKER = "dev-only-insecure";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.acceptsProfiles(Profiles.of("prod"))) {
      return;
    }
    String secret = environment.getProperty("auth.jwt.secret", "");
    if (secret.contains(DEV_SECRET_MARKER)) {
      throw new IllegalStateException(
          "auth.jwt.secret is the committed dev default; set JWT_SECRET to a real secret in prod");
    }
  }
}
