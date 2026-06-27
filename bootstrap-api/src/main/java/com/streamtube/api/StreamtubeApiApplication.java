package com.streamtube.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the StreamTube REST API.
 *
 * <p>Scans the whole {@code com.streamtube} tree so the web layer here can wire beans from
 * the {@code infrastructure} and {@code application} modules. JPA entity/repository scanning
 * is added in Phase 02 when the first persistence models arrive.
 */
@SpringBootApplication(scanBasePackages = "com.streamtube")
public class StreamtubeApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(StreamtubeApiApplication.class, args);
  }
}
