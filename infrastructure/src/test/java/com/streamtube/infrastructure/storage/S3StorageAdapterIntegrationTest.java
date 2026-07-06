package com.streamtube.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Verifies the storage adapter against real MinIO (Testcontainers): a presigned PUT then GET over
 * plain HTTP must succeed — i.e. the SigV4 signature is valid (the Java equivalent of the NestJS
 * SignatureDoesNotMatch lesson). Random container ports avoid host conflicts.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3StorageAdapterIntegrationTest {

  private static final String BUCKET = "streamtube-videos";

  @Container
  static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

  static S3StorageAdapter adapter;

  @BeforeAll
  static void setUp() {
    String url = MINIO.getS3URL();
    String user = MINIO.getUserName();
    String pass = MINIO.getPassword();

    try (S3Client admin =
        S3Client.builder()
            .endpointOverride(URI.create(url))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(user, pass)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    // Same endpoint for public/internal in the test (no Docker network split here).
    adapter = new S3StorageAdapter(url, url, BUCKET, user, pass);
  }

  @Test
  void putObjectAndObjectExists() {
    adapter.putObject("test/a.txt", "hello".getBytes(), "text/plain");
    assertThat(adapter.objectExists("test/a.txt")).isTrue();
    assertThat(adapter.objectExists("test/missing.txt")).isFalse();
  }

  @Test
  void presignedUploadThenStreamRoundTrips() throws Exception {
    String key = "videos/round-trip";
    byte[] body = "video-bytes".getBytes();
    String uploadUrl = adapter.presignUpload(key, body.length, "video/mp4");

    HttpClient http = HttpClient.newHttpClient();
    HttpResponse<Void> put =
        http.send(
            HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(put.statusCode()).isBetween(200, 204);

    assertThat(adapter.objectExists(key)).isTrue();

    String streamUrl = adapter.presignStream(key);
    HttpResponse<String> get =
        http.send(
            HttpRequest.newBuilder(URI.create(streamUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(get.statusCode()).isEqualTo(200);
    assertThat(get.body()).isEqualTo("video-bytes");
  }

  @Test
  void presignedDownloadHasAttachmentDisposition() {
    String url = adapter.presignDownload("videos/round-trip", "My Video.mp4");
    assertThat(url).contains("response-content-disposition");
  }

  /** The declared size/type are signed: storage must reject an upload that differs from them. */
  @Test
  void presignedUploadRejectsMismatchedSizeAndType() throws Exception {
    String key = "videos/mismatch";
    byte[] declared = "0123456789".getBytes(); // signed for 10 bytes, video/mp4
    String uploadUrl = adapter.presignUpload(key, declared.length, "video/mp4");

    HttpClient http = HttpClient.newHttpClient();

    HttpResponse<Void> wrongType =
        http.send(
            HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "application/pdf")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(declared))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(wrongType.statusCode()).isEqualTo(403);

    HttpResponse<Void> wrongSize =
        http.send(
            HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "video/mp4")
                .PUT(HttpRequest.BodyPublishers.ofByteArray("way-more-bytes-than-declared".getBytes()))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(wrongSize.statusCode()).isEqualTo(403);

    assertThat(adapter.objectExists(key)).isFalse();
  }
}
