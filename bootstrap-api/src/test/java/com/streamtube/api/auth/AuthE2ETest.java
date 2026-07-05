package com.streamtube.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full HTTP cycle for the auth flows against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(AuthE2ETest.CapturingMailConfig.class)
class AuthE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CapturingMailSender mail;

  @Test
  void registerConfirmLoginMeFlow() throws Exception {
    String email = "flow@test.com";
    register(email, "password123").andExpect(status().isCreated());

    String token = mail.confirmationTokens.get(email);
    assertThat(token).isNotNull();

    mockMvc
        .perform(get("/auth/confirm-email").param("token", token))
        .andExpect(status().isNoContent());

    JsonNode tokens = login(email, "password123");
    String access = tokens.get("access_token").asText();
    assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");

    mockMvc
        .perform(get("/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.confirmed").value(true))
        .andExpect(jsonPath("$.channel.nickname").exists());
  }

  @Test
  void loginBeforeConfirmationIsForbidden() throws Exception {
    String email = "unconfirmed@test.com";
    register(email, "password123").andExpect(status().isCreated());
    mockMvc
        .perform(jsonPost("/auth/login", Map.of("email", email, "password", "password123")))
        .andExpect(status().isForbidden());
  }

  @Test
  void refreshRotatesTokens() throws Exception {
    String email = "refresh@test.com";
    register(email, "password123");
    mockMvc
        .perform(get("/auth/confirm-email").param("token", mail.confirmationTokens.get(email)))
        .andExpect(status().isNoContent());
    JsonNode tokens = login(email, "password123");
    String refresh = tokens.get("refresh_token").asText();

    JsonNode rotated =
        readJson(
            mockMvc
                .perform(jsonPost("/auth/refresh", Map.of("refresh_token", refresh)))
                .andExpect(status().isOk()));
    assertThat(rotated.get("refresh_token").asText()).isNotEqualTo(refresh);
  }

  @Test
  void resetPasswordRevokesExistingSessions() throws Exception {
    String email = "reset@test.com";
    register(email, "password123");
    mockMvc
        .perform(get("/auth/confirm-email").param("token", mail.confirmationTokens.get(email)))
        .andExpect(status().isNoContent());
    JsonNode tokens = login(email, "password123");
    String refresh = tokens.get("refresh_token").asText();

    mockMvc
        .perform(jsonPost("/auth/forgot-password", Map.of("email", email)))
        .andExpect(status().isNoContent());
    String resetToken = mail.resetTokens.get(email);
    assertThat(resetToken).isNotNull();

    mockMvc
        .perform(
            jsonPost(
                "/auth/reset-password", Map.of("token", resetToken, "password", "newPassword456")))
        .andExpect(status().isNoContent());

    // the pre-reset session must be dead: its refresh token was deleted (INVALID_TOKEN)
    mockMvc
        .perform(jsonPost("/auth/refresh", Map.of("refresh_token", refresh)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

    // and the new password works
    login(email, "newPassword456");
  }

  @Test
  void meWithoutTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void duplicateEmailIsConflict() throws Exception {
    String email = "dup@test.com";
    register(email, "password123").andExpect(status().isCreated());
    register(email, "password123").andExpect(status().isConflict());
  }

  // --- helpers ---

  private ResultActions register(String email, String pw) throws Exception {
    return mockMvc.perform(jsonPost("/auth/register", Map.of("email", email, "password", pw)));
  }

  private JsonNode login(String email, String pw) throws Exception {
    return readJson(
        mockMvc
            .perform(jsonPost("/auth/login", Map.of("email", email, "password", pw)))
            .andExpect(status().isOk()));
  }

  private MockHttpServletRequestBuilder jsonPost(String path, Map<String, String> body)
      throws Exception {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body));
  }

  private JsonNode readJson(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  /** Captures emailed tokens instead of sending mail. */
  static class CapturingMailSender implements MailSender {
    final Map<String, String> confirmationTokens = new ConcurrentHashMap<>();
    final Map<String, String> resetTokens = new ConcurrentHashMap<>();

    @Override
    public void sendConfirmationEmail(String to, String rawToken) {
      confirmationTokens.put(to, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken) {
      resetTokens.put(to, rawToken);
    }
  }

  @TestConfiguration
  static class CapturingMailConfig {
    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
      return new CapturingMailSender();
    }
  }
}
