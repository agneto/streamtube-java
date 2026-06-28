package com.streamtube.api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness/landing endpoint, mirroring the reference backend's {@code GET /}. */
@RestController
@Tag(name = "health", description = "Service liveness")
public class HealthController {

  @GetMapping("/")
  @Operation(summary = "Service liveness", description = "Returns a simple service status payload.")
  public Map<String, String> root() {
    return Map.of("service", "streamtube-api", "status", "ok");
  }
}
