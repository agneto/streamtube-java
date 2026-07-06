package com.streamtube.api.config;

import com.streamtube.api.security.JwtAuthenticationFilter;
import com.streamtube.api.security.RateLimitingFilter;
import com.streamtube.api.security.SecurityErrorResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless security: default-protected endpoints with an explicit public allowlist. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RateLimitingFilter rateLimitingFilter;
  private final SecurityErrorResponses securityErrorResponses;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      RateLimitingFilter rateLimitingFilter,
      SecurityErrorResponses securityErrorResponses) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.rateLimitingFilter = rateLimitingFilter;
    this.securityErrorResponses = securityErrorResponses;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/", "/actuator/health")
                    .permitAll()
                    // Scrape endpoint: keep it reachable only from the internal network in prod.
                    .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/resend-confirmation")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/confirm-email")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/videos/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(securityErrorResponses)
                    .accessDeniedHandler(securityErrorResponses))
        .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
