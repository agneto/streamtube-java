package com.streamtube.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real PostgreSQL (Testcontainers) and asserts
 * that Flyway ran and the datasource is wired. Skipped automatically where Docker is absent.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ApplicationContextIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private DataSource dataSource;

  @Test
  void contextLoadsAndFlywayApplied() throws Exception {
    assertThat(count("SELECT count(*) FROM flyway_schema_history")).isGreaterThanOrEqualTo(1);
    assertThat(count("SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'"))
        .isEqualTo(1);
  }

  private long count(String sql) throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }
}
