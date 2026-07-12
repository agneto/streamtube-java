package com.streamtube.application.port.out;

import java.util.List;

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

  /**
   * Presigned GET URL with an explicit TTL (public host). HLS segments use this: a VOD player
   * fetches the rendition playlist once and must be able to play to the end — the default read
   * TTL would 403 a long video mid-playback.
   */
  String presignStream(String key, long ttlSeconds);

  /** Reads a small UTF-8 text object (HLS playlists) into memory. */
  String getObjectText(String key);

  /** Presigned GET URL forcing a download with the given filename (public host). */
  String presignDownload(String key, String filename);

  /** Presigned GET URL for server/worker-side reads (internal host). */
  String presignInternal(String key);

  void putObject(String key, byte[] body, String contentType);

  boolean objectExists(String key);

  /** Opens a multipart upload session for the key; the returned uploadId identifies it. */
  String createMultipartUpload(String key, String contentType);

  /**
   * Presigned PUT URL for one part (public host, longer TTL than the single PUT). The exact
   * content length of THAT part is signed — the last part's remainder, not the nominal size.
   */
  String presignUploadPart(String key, String uploadId, int partNumber, long contentLength);

  /** Parts already uploaded for the session (ETags included — they never leave the server). */
  List<UploadedPart> listUploadedParts(String key, String uploadId);

  /** Assembles the final object from the uploaded parts. */
  void completeMultipartUpload(String key, String uploadId, List<UploadedPart> parts);

  /** Discards the session and every uploaded part. */
  void abortMultipartUpload(String key, String uploadId);

  /** Size of a stored object (final size check after multipart completion). */
  long objectSizeBytes(String key);

  /** Removes an object (assembled multipart object whose size failed verification). */
  void deleteObject(String key);

  /** Removes every object under {@code prefix} (deleting an empty prefix is a no-op). */
  void deleteObjectsByPrefix(String prefix);
}
