package com.streamtube.api.testsupport;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.UploadedPart;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory StoragePort for E2E tests, shared by every test config. Single-PUT objects "always
 * exist" (the historical behavior the upload tests rely on); the multipart session lifecycle is
 * real enough to exercise retry/resume: parts are registered via {@link #receivePart}, listed,
 * and only complete materializes the object with the sum of the part sizes.
 */
public class FakeStorage implements StoragePort {

  /** uploadId -> partNumber -> size. */
  private final Map<String, Map<Integer, Long>> sessions = new ConcurrentHashMap<>();

  /** key -> size of completed multipart objects. */
  private final Map<String, Long> objects = new ConcurrentHashMap<>();

  /** key -> content of small text objects (HLS playlists seeded by tests). */
  private final Map<String, String> textObjects = new ConcurrentHashMap<>();

  @Override
  public String presignUpload(String key, long contentLength, String contentType) {
    return "http://localhost:9000/" + key + "?upload&sig=x";
  }

  @Override
  public String presignStream(String key) {
    return "http://localhost:9000/" + key + "?stream&sig=x";
  }

  @Override
  public String presignStream(String key, long ttlSeconds) {
    return "http://localhost:9000/" + key + "?stream&ttl=" + ttlSeconds + "&X-Amz-Signature=fake";
  }

  @Override
  public String presignDownload(String key, String filename) {
    return "http://localhost:9000/" + key + "?download&response-content-disposition=attachment";
  }

  @Override
  public String presignInternal(String key) {
    return "http://minio:9000/" + key + "?internal&sig=x";
  }

  @Override
  public void putObject(String key, byte[] body, String contentType) {}

  @Override
  public boolean objectExists(String key) {
    // Historical behavior: uploaded objects "always exist". HLS keys are the exception — the
    // rendition-not-in-ladder 404 path needs a real absence signal.
    return !key.startsWith("hls/") || textObjects.containsKey(key);
  }

  @Override
  public String getObjectText(String key) {
    String text = textObjects.get(key);
    if (text == null) {
      throw new IllegalStateException("No text object seeded for " + key);
    }
    return text;
  }

  /** Test hook: seeds a playlist as if the worker had uploaded it. */
  public void putTextObject(String key, String content) {
    textObjects.put(key, content);
  }

  @Override
  public String createMultipartUpload(String key, String contentType) {
    String uploadId = UUID.randomUUID().toString();
    sessions.put(uploadId, new ConcurrentHashMap<>());
    return uploadId;
  }

  @Override
  public String presignUploadPart(String key, String uploadId, int partNumber, long contentLength) {
    return "http://localhost:9000/" + key + "?partNumber=" + partNumber + "&uploadId=" + uploadId;
  }

  @Override
  public List<UploadedPart> listUploadedParts(String key, String uploadId) {
    return sessions.getOrDefault(uploadId, Map.of()).entrySet().stream()
        .map(e -> new UploadedPart(e.getKey(), e.getValue(), "etag-" + e.getKey()))
        .sorted(Comparator.comparingInt(UploadedPart::partNumber))
        .toList();
  }

  @Override
  public void completeMultipartUpload(String key, String uploadId, List<UploadedPart> parts) {
    long total = parts.stream().mapToLong(UploadedPart::sizeBytes).sum();
    objects.put(key, total);
    sessions.remove(uploadId);
  }

  @Override
  public void abortMultipartUpload(String key, String uploadId) {
    sessions.remove(uploadId);
  }

  @Override
  public long objectSizeBytes(String key) {
    return objects.getOrDefault(key, 0L);
  }

  @Override
  public void deleteObject(String key) {
    objects.remove(key);
  }

  @Override
  public void deleteObjectsByPrefix(String prefix) {
    objects.keySet().removeIf(k -> k.startsWith(prefix));
    textObjects.keySet().removeIf(k -> k.startsWith(prefix));
    deletedPrefixes.add(prefix);
  }

  /** Test hook: prefixes wiped by the cleanup sweeper, in order. */
  public final java.util.List<String> deletedPrefixes =
      java.util.Collections.synchronizedList(new java.util.ArrayList<>());

  /** Test hook: simulates the client PUTting one part to its presigned URL. */
  public void receivePart(String uploadId, int partNumber, long sizeBytes) {
    sessions.computeIfAbsent(uploadId, k -> new ConcurrentHashMap<>()).put(partNumber, sizeBytes);
  }

  /** Test hook: the uploadId of the only open session (E2E runs one multipart at a time). */
  public String onlyOpenUploadId() {
    if (sessions.size() != 1) {
      throw new IllegalStateException("Expected exactly one open session, got " + sessions.size());
    }
    return sessions.keySet().iterator().next();
  }
}
