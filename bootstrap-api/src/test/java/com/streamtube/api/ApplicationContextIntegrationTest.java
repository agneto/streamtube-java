package com.streamtube.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a real PostgreSQL (Testcontainers) and asserts
 * that Flyway ran and the datasource is wired. Skipped automatically where Docker is absent.
 */
@SpringBootTest
@AutoConfigureMockMvc
// Metrics exporters are disabled in tests by default; this re-enables the prometheus endpoint.
@AutoConfigureObservability
@Testcontainers(disabledWithoutDocker = true)
class ApplicationContextIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mockMvc;

  @Test
  void contextLoadsAndFlywayApplied() throws Exception {
    assertThat(count("SELECT count(*) FROM flyway_schema_history")).isGreaterThanOrEqualTo(1);
    assertThat(count("SELECT count(*) FROM pg_extension WHERE extname = 'pgcrypto'"))
        .isEqualTo(1);
  }

  @Test
  void prometheusEndpointExposesJvmMetrics() throws Exception {
    String body =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("jvm_memory_used_bytes");
    assertThat(body).contains("application=\"streamtube-api\"");
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
