package com.streamtube.infrastructure.security;

import com.streamtube.application.port.out.NicknameGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Derives a channel nickname from the email local part plus a short random suffix. */
@Component
public class NicknameGeneratorImpl implements NicknameGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

  @Override
  public String generate(String email) {
    String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
    String base = local.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (base.isEmpty()) {
      base = "user";
    }
    if (base.length() > 40) {
      base = base.substring(0, 40);
    }
    StringBuilder suffix = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      suffix.append(SUFFIX_CHARS.charAt(RANDOM.nextInt(SUFFIX_CHARS.length())));
    }
    return base + "-" + suffix;
  }
}
