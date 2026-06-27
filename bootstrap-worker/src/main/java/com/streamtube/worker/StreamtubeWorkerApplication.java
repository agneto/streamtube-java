package com.streamtube.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Entry point for the StreamTube video-processing worker.
 *
 * <p>Phase 01 placeholder: the module is wired into the Clean Architecture build and boots
 * standalone, but its RabbitMQ listener and FFmpeg processing are implemented in Phase 03.
 * Datasource/JPA/Flyway auto-configuration is excluded for now so the worker can start
 * without provisioning a database; it is re-enabled when real processing lands.
 */
@SpringBootApplication(
    scanBasePackages = "com.streamtube.worker",
    exclude = {
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
    })
public class StreamtubeWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(StreamtubeWorkerApplication.class, args);
  }
}
