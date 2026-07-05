package com.streamtube.worker;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * StreamTube video-processing worker. Consumes the RabbitMQ processing queue and runs FFmpeg.
 *
 * <p>Scans only the packages it needs (worker + video use cases + storage/messaging/persistence
 * adapters) so it does not pull in API-only concerns (auth, JWT, mail). It shares the database with
 * the API (which owns the Flyway migrations); the worker itself runs no migrations.
 */
@SpringBootApplication(
    scanBasePackages = {
      "com.streamtube.worker",
      "com.streamtube.infrastructure.storage",
      "com.streamtube.infrastructure.messaging",
      "com.streamtube.infrastructure.persistence",
      "com.streamtube.infrastructure.transaction"
    })
@EntityScan("com.streamtube.infrastructure.persistence.entity")
@EnableJpaRepositories("com.streamtube.infrastructure.persistence.repository")
public class StreamtubeWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(StreamtubeWorkerApplication.class, args);
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
