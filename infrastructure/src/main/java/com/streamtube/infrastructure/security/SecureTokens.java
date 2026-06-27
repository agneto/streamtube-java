package com.streamtube.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Generates opaque token secrets and computes their SHA-256 hashes for storage/lookup. */
public final class SecureTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private SecureTokens() {}

  /** A 256-bit URL-safe random secret. */
  public static String randomRawValue() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return URL_ENCODER.encodeToString(bytes);
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
