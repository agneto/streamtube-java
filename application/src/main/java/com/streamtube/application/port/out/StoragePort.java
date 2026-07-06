package com.streamtube.application.port.out;

/** Output port for object storage (MinIO/S3). Presigned URLs keep file bytes out of the API. */
public interface StoragePort {

  /**
   * Presigned PUT URL for the client to upload directly (public host). The content length and
   * type are part of the signature: storage rejects an upload whose actual size or type differ
   * from what was declared here.
   */
  String presignUpload(String key, long contentLength, String contentType);

  /** Presigned GET URL for streaming (public host). */
  String presignStream(String key);

  /** Presigned GET URL forcing a download with the given filename (public host). */
  String presignDownload(String key, String filename);

  /** Presigned GET URL for server/worker-side reads (internal host). */
  String presignInternal(String key);

  void putObject(String key, byte[] body, String contentType);

  boolean objectExists(String key);
}
