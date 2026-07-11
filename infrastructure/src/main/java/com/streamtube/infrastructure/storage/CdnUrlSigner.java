package com.streamtube.infrastructure.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;

/**
 * Signs CDN read URLs in the nginx {@code secure_link} format — the de-facto token-auth scheme of
 * commercial CDNs (BunnyCDN/KeyCDN-compatible): {@code st = base64url(md5(expires + uri +
 * secret))} without padding, appended as {@code ?st=...&e=...}. The edge and this signer must
 * agree on the exact md5 input; the nginx conf declares the same concatenation.
 */
public class CdnUrlSigner {

  private final String baseUrl;
  private final String basePath;
  private final String secret;
  private final Clock clock;

  public CdnUrlSigner(String baseUrl, String secret, Clock clock) {
    if (baseUrl == null || baseUrl.isBlank() || secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "cdn.enabled=true requires cdn.base-url and cdn.secret (fail fast at boot,"
              + " never mint URLs against a null host at request time)");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.basePath = URI.create(this.baseUrl).getPath();
    this.secret = secret;
    this.clock = clock;
  }

  /** Token-authenticated URL for {@code key}, valid for {@code ttlSeconds}. */
  public String sign(String key, long ttlSeconds) {
    long expires = clock.instant().getEpochSecond() + ttlSeconds;
    String uri = basePath + "/" + key;
    return baseUrl + "/" + key + "?st=" + token(expires, uri) + "&e=" + expires;
  }

  /**
   * Download variant: {@code dl} carries the filename for the edge to set Content-Disposition.
   * It stays OUTSIDE the token on purpose — the same object signed with and without disposition
   * must validate identically.
   */
  public String signDownload(String key, long ttlSeconds, String filename) {
    return sign(key, ttlSeconds)
        + "&dl="
        + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String token(long expires, String uri) {
    try {
      byte[] md5 =
          MessageDigest.getInstance("MD5")
              .digest((expires + uri + secret).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(md5);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }
}
