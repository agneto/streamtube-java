package com.streamtube.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The token vector below was computed independently (python hashlib/base64), not with this class:
 * the signer and the nginx conf must agree byte-for-byte, so the test must not be circular.
 */
class CdnUrlSignerTest {

  // md5("1750000000/streamtube-videos/videos/abc123" + "s3cr3t"), base64url without padding
  private static final String EXPECTED_TOKEN = "0wdCz0M0Gy23-lXNKmBLRA";

  private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_749_999_400), ZoneOffset.UTC);

  @Test
  void signsTheSecureLinkVector() {
    CdnUrlSigner signer =
        new CdnUrlSigner("http://localhost:8090/streamtube-videos", "s3cr3t", clock);

    // 1_749_999_400 + 600s TTL = expires 1_750_000_000 (the vector's timestamp)
    String url = signer.sign("videos/abc123", 600);

    assertThat(url)
        .isEqualTo(
            "http://localhost:8090/streamtube-videos/videos/abc123?st="
                + EXPECTED_TOKEN
                + "&e=1750000000");
  }

  @Test
  void downloadKeepsTheFilenameOutsideTheToken() {
    CdnUrlSigner signer =
        new CdnUrlSigner("http://localhost:8090/streamtube-videos", "s3cr3t", clock);

    String url = signer.signDownload("videos/abc123", 600, "Meu Vídeo.mp4");

    // same st token as the plain sign — dl must not participate in the signature
    assertThat(url)
        .startsWith(
            "http://localhost:8090/streamtube-videos/videos/abc123?st=" + EXPECTED_TOKEN)
        .endsWith("&dl=Meu%20V%C3%ADdeo.mp4");
  }

  @Test
  void trailingSlashOnBaseUrlIsNormalized() {
    CdnUrlSigner signer =
        new CdnUrlSigner("http://localhost:8090/streamtube-videos/", "s3cr3t", clock);

    assertThat(signer.sign("videos/abc123", 600)).contains("st=" + EXPECTED_TOKEN);
  }

  @Test
  void missingConfigFailsFast() {
    assertThatThrownBy(() -> new CdnUrlSigner("", "s3cr3t", clock))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new CdnUrlSigner("http://cdn", " ", clock))
        .isInstanceOf(IllegalStateException.class);
  }
}
