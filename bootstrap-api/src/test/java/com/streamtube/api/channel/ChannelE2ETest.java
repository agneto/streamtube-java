package com.streamtube.api.channel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamtube.application.port.out.MailSender;
import java.util.HashMap;
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

/** Full HTTP cycle for channel description editing (real Postgres via Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(ChannelE2ETest.ChannelTestConfig.class)
class ChannelE2ETest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CapturingMailSender mail;

  @Test
  void ownerUpdatesChannelDescription() throws Exception {
    String token = registerConfirmLogin("channel-owner@test.com");

    mockMvc
        .perform(
            patch("/channels/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("description", "Minha nova bio"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Minha nova bio"))
        .andExpect(jsonPath("$.nickname").exists());
  }

  @Test
  void canClearDescriptionWithNull() throws Exception {
    String token = registerConfirmLogin("channel-clear@test.com");

    // a Map with a null value (Map.of doesn't allow null) to send {"description": null}
    Map<String, String> body = new HashMap<>();
    body.put("description", null);

    mockMvc
        .perform(
            patch("/channels/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());
  }

  @Test
  void updateRequiresAuth() throws Exception {
    mockMvc
        .perform(
            patch("/channels/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("description", "x"))))
        .andExpect(status().isUnauthorized());
  }

  // --- helpers ---

  private String registerConfirmLogin(String email) throws Exception {
    mockMvc
        .perform(jsonPost("/auth/register", Map.of("email", email, "password", "password123")))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/auth/confirm-email").param("token", mail.confirmationTokens.get(email)))
        .andExpect(status().isNoContent());
    JsonNode tokens =
        readJson(
            mockMvc
                .perform(jsonPost("/auth/login", Map.of("email", email, "password", "password123")))
                .andExpect(status().isOk()));
    return tokens.get("access_token").asText();
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

  static class CapturingMailSender implements MailSender {
    final Map<String, String> confirmationTokens = new ConcurrentHashMap<>();

    @Override
    public void sendConfirmationEmail(String to, String rawToken) {
      confirmationTokens.put(to, rawToken);
    }

    @Override
    public void sendPasswordResetEmail(String to, String rawToken) {}
  }

  @TestConfiguration
  static class ChannelTestConfig {
    @Bean
    @Primary
    CapturingMailSender capturingMailSender() {
      return new CapturingMailSender();
    }
  }
}
