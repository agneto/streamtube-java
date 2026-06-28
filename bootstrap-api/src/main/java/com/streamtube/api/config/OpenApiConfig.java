package com.streamtube.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the generated Swagger UI / {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI streamtubeOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("StreamTube API")
                .description("StreamTube backend (Java 21 / Spring Boot, Clean Architecture)")
                .version("0.1.0"));
  }
}
