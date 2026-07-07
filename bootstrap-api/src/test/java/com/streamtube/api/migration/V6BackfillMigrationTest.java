package com.streamtube.api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Asserts the V6 backfill on real Postgres: videos that were already READY before the publication
 * concept existed must come out published (published_at = updated_at), while non-READY videos stay
 * drafts. Runs Flyway up to V5, inserts pre-existing rows, then applies V6.
 */
@Testcontainers(disabledWithoutDocker = true)
class V6BackfillMigrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Test
  void v6BackfillPublishesPreexistingReadyVideosOnly() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("5")
        .load()
        .migrate();

    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement st = c.createStatement()) {
      st.execute(
          """
          INSERT INTO users (id, email, password) VALUES
              ('00000000-0000-0000-0000-000000000001', 'legacy@test.com', 'hash');
          INSERT INTO channels (id, user_id, name, nickname) VALUES
              ('00000000-0000-0000-0000-000000000002',
               '00000000-0000-0000-0000-000000000001', 'Legacy', 'legacy');
          INSERT INTO videos (channel_id, title, slug, status, storage_key, updated_at) VALUES
              ('00000000-0000-0000-0000-000000000002', 'Watchable', 'ready0000ready00',
               'READY', 'videos/ready', '2026-01-01T00:00:00Z'),
              ('00000000-0000-0000-0000-000000000002', 'Still processing', 'pend00000pend000',
               'PROCESSING', 'videos/pending', '2026-01-01T00:00:00Z');
          """);
    }

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();

    try (Connection c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT slug, status, visibility, published_at, updated_at FROM videos ORDER BY slug")) {

      rs.next(); // pend00000pend000
      assertThat(rs.getString("visibility")).isEqualTo("PUBLIC"); // column default
      assertThat(rs.getTimestamp("published_at")).isNull(); // stays a draft

      rs.next(); // ready0000ready00
      assertThat(rs.getString("visibility")).isEqualTo("PUBLIC");
      Timestamp publishedAt = rs.getTimestamp("published_at");
      assertThat(publishedAt).isNotNull().isEqualTo(rs.getTimestamp("updated_at"));
    }
  }
}
