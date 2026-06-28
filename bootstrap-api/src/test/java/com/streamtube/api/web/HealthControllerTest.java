package com.streamtube.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain unit test for the health endpoint payload (no Spring context). */
class HealthControllerTest {

  @Test
  void rootReturnsServiceStatus() {
    Map<String, String> body = new HealthController().root();
    assertThat(body).containsEntry("service", "streamtube-api").containsEntry("status", "ok");
  }
}
