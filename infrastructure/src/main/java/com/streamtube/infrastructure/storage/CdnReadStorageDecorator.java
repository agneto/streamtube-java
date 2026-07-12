package com.streamtube.infrastructure.storage;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.UploadedPart;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * CDN profile ({@code CDN_ENABLED=true}): the three public READ-URL methods produce
 * token-authenticated CDN URLs; everything else — uploads (single PUT and multipart parts),
 * worker-internal reads, object operations — delegates to the S3 adapter untouched, because CDNs
 * do not take uploads and the worker reads the origin directly. With the property off this bean
 * does not exist and behavior is bit-for-bit the presigned default.
 */
@Component
@Primary
@ConditionalOnProperty(name = "cdn.enabled", havingValue = "true")
public class CdnReadStorageDecorator implements StoragePort {

  // Mirrors the S3 adapter's read TTL: the CDN token window matches the presigned one.
  private static final long READ_TTL_SECONDS = 3600;

  private final S3StorageAdapter delegate;
  private final CdnUrlSigner signer;

  public CdnReadStorageDecorator(
      S3StorageAdapter delegate,
      @Value("${cdn.base-url:}") String baseUrl,
      @Value("${cdn.secret:}") String secret,
      Clock clock) {
    this.delegate = delegate;
    this.signer = new CdnUrlSigner(baseUrl, secret, clock); // fails fast on missing config
  }

  @Override
  public String presignStream(String key) {
    return signer.sign(key, READ_TTL_SECONDS);
  }

  @Override
  public String presignStream(String key, long ttlSeconds) {
    return signer.sign(key, ttlSeconds); // HLS segments keep their long-TTL semantics
  }

  @Override
  public String presignDownload(String key, String filename) {
    return signer.signDownload(key, READ_TTL_SECONDS, filename);
  }

  @Override
  public String presignUpload(String key, long contentLength, String contentType) {
    return delegate.presignUpload(key, contentLength, contentType);
  }

  @Override
  public String presignInternal(String key) {
    return delegate.presignInternal(key);
  }

  @Override
  public void putObject(String key, byte[] body, String contentType) {
    delegate.putObject(key, body, contentType);
  }

  @Override
  public boolean objectExists(String key) {
    return delegate.objectExists(key);
  }

  @Override
  public String getObjectText(String key) {
    return delegate.getObjectText(key);
  }

  @Override
  public String createMultipartUpload(String key, String contentType) {
    return delegate.createMultipartUpload(key, contentType);
  }

  @Override
  public String presignUploadPart(String key, String uploadId, int partNumber, long contentLength) {
    return delegate.presignUploadPart(key, uploadId, partNumber, contentLength);
  }

  @Override
  public List<UploadedPart> listUploadedParts(String key, String uploadId) {
    return delegate.listUploadedParts(key, uploadId);
  }

  @Override
  public void completeMultipartUpload(String key, String uploadId, List<UploadedPart> parts) {
    delegate.completeMultipartUpload(key, uploadId, parts);
  }

  @Override
  public void abortMultipartUpload(String key, String uploadId) {
    delegate.abortMultipartUpload(key, uploadId);
  }

  @Override
  public long objectSizeBytes(String key) {
    return delegate.objectSizeBytes(key);
  }

  @Override
  public void deleteObject(String key) {
    delegate.deleteObject(key);
  }

  @Override
  public void deleteObjectsByPrefix(String prefix) {
    delegate.deleteObjectsByPrefix(prefix);
  }
}
