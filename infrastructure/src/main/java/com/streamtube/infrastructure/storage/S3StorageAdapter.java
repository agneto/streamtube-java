package com.streamtube.infrastructure.storage;

import com.streamtube.application.port.out.StoragePort;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3/MinIO storage adapter. Uses two presigners because {@code host} is a SigV4-signed header:
 * client-facing URLs are signed against the public host, server/worker URLs against the internal
 * host. (Signing internally and rewriting the host yields {@code SignatureDoesNotMatch}.)
 */
@Component
public class S3StorageAdapter implements StoragePort {

  private static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
  private static final Duration READ_TTL = Duration.ofHours(1);

  private final S3Client internalClient;
  private final S3Presigner publicPresigner;
  private final S3Presigner internalPresigner;
  private final String bucket;

  public S3StorageAdapter(
      @Value("${storage.endpoint}") String endpoint,
      @Value("${storage.public-url}") String publicUrl,
      @Value("${storage.bucket}") String bucket,
      @Value("${storage.access-key}") String accessKey,
      @Value("${storage.secret-key}") String secretKey) {
    this.bucket = bucket;
    StaticCredentialsProvider creds =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

    this.internalClient =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .serviceConfiguration(pathStyle)
            .build();
    this.internalPresigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .serviceConfiguration(pathStyle)
            .build();
    this.publicPresigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(publicUrl))
            .region(Region.US_EAST_1)
            .credentialsProvider(creds)
            .serviceConfiguration(pathStyle)
            .build();
  }

  @Override
  public String presignUpload(String key, long contentLength, String contentType) {
    PutObjectRequest put =
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentLength(contentLength)
            .contentType(contentType)
            .build();
    PutObjectPresignRequest req =
        PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_TTL)
            .putObjectRequest(put)
            .build();
    return publicPresigner.presignPutObject(req).url().toString();
  }

  @Override
  public String presignStream(String key) {
    return publicPresigner
        .presignGetObject(getRequest(key, null, READ_TTL))
        .url()
        .toString();
  }

  @Override
  public String presignDownload(String key, String filename) {
    String disposition = "attachment; filename=\"" + filename + "\"";
    return publicPresigner
        .presignGetObject(getRequest(key, disposition, READ_TTL))
        .url()
        .toString();
  }

  @Override
  public String presignInternal(String key) {
    return internalPresigner
        .presignGetObject(getRequest(key, null, READ_TTL))
        .url()
        .toString();
  }

  @Override
  public void putObject(String key, byte[] body, String contentType) {
    internalClient.putObject(
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(body));
  }

  @Override
  public boolean objectExists(String key) {
    try {
      internalClient.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  private GetObjectPresignRequest getRequest(String key, String disposition, Duration ttl) {
    GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(key);
    if (disposition != null) {
      get.responseContentDisposition(disposition);
    }
    return GetObjectPresignRequest.builder()
        .signatureDuration(ttl)
        .getObjectRequest(get.build())
        .build();
  }
}
