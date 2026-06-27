package com.streamtube.api.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP token-bucket rate limiting on auth-sensitive POST endpoints (Bucket4j). */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Set<String> LIMITED_PATHS =
      Set.of(
          "/auth/login",
          "/auth/register",
          "/auth/forgot-password",
          "/auth/resend-confirmation",
          "/auth/refresh");

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (!"POST".equalsIgnoreCase(request.getMethod())
        || !LIMITED_PATHS.contains(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    String key = request.getRemoteAddr() + ":" + request.getRequestURI();
    Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

    if (bucket.tryConsume(1)) {
      filterChain.doFilter(request, response);
    } else {
      response.setStatus(429);
      response.setContentType("application/json");
      response
          .getWriter()
          .write(
              "{\"statusCode\":429,\"code\":\"RATE_LIMITED\","
                  + "\"message\":\"Too many requests\"}");
    }
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(10)
            .refillGreedy(10, Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }
}
