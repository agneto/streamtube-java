package com.streamtube.infrastructure.storage;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.UploadedPart;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

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
  private final Duration partUrlTtl;

  public S3StorageAdapter(
      @Value("${storage.endpoint}") String endpoint,
      @Value("${storage.public-url}") String publicUrl,
      @Value("${storage.bucket}") String bucket,
      @Value("${storage.access-key}") String accessKey,
      @Value("${storage.secret-key}") String secretKey,
      @Value("${upload.part-url-ttl-seconds:3600}") long partUrlTtlSeconds) {
    this.bucket = bucket;
    // Slow connections take long per part; expired part URLs are simply re-requested.
    this.partUrlTtl = Duration.ofSeconds(partUrlTtlSeconds);
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
  public String presignStream(String key, long ttlSeconds) {
    return publicPresigner
        .presignGetObject(getRequest(key, null, Duration.ofSeconds(ttlSeconds)))
        .url()
        .toString();
  }

  @Override
  public String getObjectText(String key) {
    try (var stream =
        internalClient.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Failed to read object " + key, e);
    }
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

  @Override
  public String createMultipartUpload(String key, String contentType) {
    return internalClient
        .createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build())
        .uploadId();
  }

  @Override
  public String presignUploadPart(String key, String uploadId, int partNumber, long contentLength) {
    UploadPartRequest part =
        UploadPartRequest.builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .contentLength(contentLength)
            .build();
    return publicPresigner
        .presignUploadPart(
            UploadPartPresignRequest.builder()
                .signatureDuration(partUrlTtl)
                .uploadPartRequest(part)
                .build())
        .url()
        .toString();
  }

  @Override
  public List<UploadedPart> listUploadedParts(String key, String uploadId) {
    // ListParts pages at 1000 — iterate to exhaustion or big uploads complete with parts missing.
    List<UploadedPart> parts = new ArrayList<>();
    Integer marker = null;
    ListPartsResponse page;
    do {
      ListPartsRequest.Builder request =
          ListPartsRequest.builder().bucket(bucket).key(key).uploadId(uploadId);
      if (marker != null) {
        request.partNumberMarker(marker);
      }
      page = internalClient.listParts(request.build());
      page.parts()
          .forEach(p -> parts.add(new UploadedPart(p.partNumber(), p.size(), p.eTag())));
      marker = page.nextPartNumberMarker();
    } while (Boolean.TRUE.equals(page.isTruncated()));
    return parts;
  }

  @Override
  public void completeMultipartUpload(String key, String uploadId, List<UploadedPart> parts) {
    List<CompletedPart> completed =
        parts.stream()
            .sorted(java.util.Comparator.comparingInt(UploadedPart::partNumber))
            .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build())
            .toList();
    internalClient.completeMultipartUpload(
        CompleteMultipartUploadRequest.builder()
            .bucket(bucket)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
            .build());
  }

  @Override
  public void abortMultipartUpload(String key, String uploadId) {
    try {
      internalClient.abortMultipartUpload(
          AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build());
    } catch (S3Exception e) {
      // Session already gone (e.g. consumed by a complete whose size check then failed): the
      // outcome the caller wants — no parts left — is already true.
      if (e.statusCode() != 404) {
        throw e;
      }
    }
  }

  @Override
  public long objectSizeBytes(String key) {
    return internalClient
        .headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        .contentLength();
  }

  @Override
  public void deleteObject(String key) {
    internalClient.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @Override
  public void deleteObjectsByPrefix(String prefix) {
    // Listings page at 1000 and DeleteObjects caps at 1000 keys — iterate to exhaustion, or a
    // long video's HLS ladder outlives its "deletion".
    String continuationToken = null;
    software.amazon.awssdk.services.s3.model.ListObjectsV2Response page;
    do {
      software.amazon.awssdk.services.s3.model.ListObjectsV2Request.Builder listRequest =
          software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
              .bucket(bucket)
              .prefix(prefix);
      if (continuationToken != null) {
        listRequest.continuationToken(continuationToken);
      }
      page = internalClient.listObjectsV2(listRequest.build());
      if (!page.contents().isEmpty()) {
        List<software.amazon.awssdk.services.s3.model.ObjectIdentifier> keys =
            page.contents().stream()
                .map(
                    o ->
                        software.amazon.awssdk.services.s3.model.ObjectIdentifier.builder()
                            .key(o.key())
                            .build())
                .toList();
        internalClient.deleteObjects(
            software.amazon.awssdk.services.s3.model.DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(
                    software.amazon.awssdk.services.s3.model.Delete.builder()
                        .objects(keys)
                        .build())
                .build());
      }
      continuationToken = page.nextContinuationToken();
    } while (Boolean.TRUE.equals(page.isTruncated()));
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
