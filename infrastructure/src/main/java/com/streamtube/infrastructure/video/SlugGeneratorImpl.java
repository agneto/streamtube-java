package com.streamtube.infrastructure.video;

import com.streamtube.application.port.out.SlugGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** 11-character URL-safe slug from 64 bits of randomness (YouTube-style). */
@Component
public class SlugGeneratorImpl implements SlugGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  @Override
  public String generate() {
    byte[] bytes = new byte[8];
    RANDOM.nextBytes(bytes);
    return URL_ENCODER.encodeToString(bytes);
  }
}
