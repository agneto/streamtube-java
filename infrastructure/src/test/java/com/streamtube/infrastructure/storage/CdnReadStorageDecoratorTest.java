package com.streamtube.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Delegation matrix: reads become CDN URLs, everything else reaches the S3 adapter untouched. */
class CdnReadStorageDecoratorTest {

  private S3StorageAdapter delegate;
  private CdnReadStorageDecorator decorator;

  @BeforeEach
  void setUp() {
    delegate = Mockito.mock(S3StorageAdapter.class);
    decorator =
        new CdnReadStorageDecorator(
            delegate,
            "http://cdn:8090/streamtube-videos",
            "secret",
            Clock.fixed(Instant.ofEpochSecond(1_750_000_000), ZoneOffset.UTC));
  }

  @Test
  void readUrlsPointAtTheCdn() {
    assertThat(decorator.presignStream("videos/x")).startsWith("http://cdn:8090/").contains("st=");
    assertThat(decorator.presignStream("hls/x/360p/seg-000.ts", 21600))
        .startsWith("http://cdn:8090/")
        .contains("&e=" + (1_750_000_000 + 21600));
    assertThat(decorator.presignDownload("videos/x", "f.mp4")).contains("&dl=f.mp4");
    Mockito.verifyNoInteractions(delegate);
  }

  @Test
  void uploadsInternalReadsAndObjectOpsDelegate() {
    decorator.presignUpload("videos/x", 10, "video/mp4");
    verify(delegate).presignUpload("videos/x", 10, "video/mp4");
    decorator.presignInternal("videos/x");
    verify(delegate).presignInternal("videos/x");
    decorator.createMultipartUpload("videos/x", "video/mp4");
    verify(delegate).createMultipartUpload("videos/x", "video/mp4");
    decorator.presignUploadPart("videos/x", "up", 1, 5);
    verify(delegate).presignUploadPart("videos/x", "up", 1, 5);
    decorator.listUploadedParts("videos/x", "up");
    verify(delegate).listUploadedParts("videos/x", "up");
    decorator.completeMultipartUpload("videos/x", "up", List.of());
    verify(delegate).completeMultipartUpload("videos/x", "up", List.of());
    decorator.abortMultipartUpload("videos/x", "up");
    verify(delegate).abortMultipartUpload("videos/x", "up");
    decorator.objectExists("videos/x");
    verify(delegate).objectExists("videos/x");
    decorator.objectSizeBytes("videos/x");
    verify(delegate).objectSizeBytes("videos/x");
    decorator.getObjectText("hls/x/master.m3u8");
    verify(delegate).getObjectText("hls/x/master.m3u8");
    decorator.deleteObject("videos/x");
    verify(delegate).deleteObject("videos/x");
    decorator.putObject("k", new byte[0], "t");
    verify(delegate).putObject("k", new byte[0], "t");
  }
}
