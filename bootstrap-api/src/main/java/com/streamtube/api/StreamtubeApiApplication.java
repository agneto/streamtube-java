package com.streamtube.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the StreamTube REST API.
 *
 * <p>Component-scans the whole {@code com.streamtube} tree so the web layer wires beans from the
 * {@code application} and {@code infrastructure} modules. JPA entities and Spring Data repositories
 * live in the infrastructure module, so they are registered explicitly here.
 */
// The social and notification slices are registered here only: the worker's persistence unit
// never maps reactions/comments/subscriptions/notifications — tables it never touches.
@SpringBootApplication(scanBasePackages = "com.streamtube")
@EntityScan({
  "com.streamtube.infrastructure.persistence.entity",
  "com.streamtube.infrastructure.social",
  "com.streamtube.infrastructure.notification"
})
@EnableJpaRepositories({
  "com.streamtube.infrastructure.persistence.repository",
  "com.streamtube.infrastructure.social",
  "com.streamtube.infrastructure.notification"
})
public class StreamtubeApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(StreamtubeApiApplication.class, args);
  }
}
