package com.streamtube.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.streamtube.api.web.dto.ErrorEnvelope;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP token-bucket rate limiting on auth-sensitive endpoints (Bucket4j).
 *
 * <p>The client IP comes from {@code getRemoteAddr()}, which Tomcat's RemoteIp valve resolves from
 * {@code X-Forwarded-For} when (and only when) the direct peer is a trusted proxy — see {@code
 * server.tomcat.remoteip} in application.yml.
 *
 * <p>Buckets live in a bounded Caffeine cache with idle expiry, so the per-IP state cannot grow
 * without limit. Expiry is lossless: a bucket refills completely in {@link #REFILL_PERIOD}, well
 * before it can be evicted as idle.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final int CAPACITY = 10;
  private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

  private static final Set<String> LIMITED_POST_PATHS =
      Set.of(
          "/auth/login",
          "/auth/register",
          "/auth/forgot-password",
          "/auth/reset-password",
          "/auth/resend-confirmation",
          "/auth/refresh");

  private static final Set<String> LIMITED_GET_PATHS = Set.of("/auth/confirm-email");

  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder()
          .maximumSize(100_000)
          .expireAfterAccess(Duration.ofMinutes(5))
          .build();

  private final ObjectMapper objectMapper;

  public RateLimitingFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (!isLimited(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = request.getRemoteAddr() + ":" + request.getRequestURI();
    Bucket bucket = buckets.get(key, k -> newBucket());

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }

    long retryAfterSeconds =
        Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            objectMapper.writeValueAsString(
                new ErrorEnvelope(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMITED",
                    "Too many requests",
                    Instant.now(),
                    request.getRequestURI())));
  }

  private boolean isLimited(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return switch (request.getMethod()) {
      case "POST" -> LIMITED_POST_PATHS.contains(uri);
      case "GET" -> LIMITED_GET_PATHS.contains(uri);
      default -> false;
    };
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder().capacity(CAPACITY).refillGreedy(CAPACITY, REFILL_PERIOD).build();
    return Bucket.builder().addLimit(limit).build();
  }
}
