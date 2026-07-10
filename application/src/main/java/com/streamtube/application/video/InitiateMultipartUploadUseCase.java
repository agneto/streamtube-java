package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.video.result.InitiateMultipartResult;
import com.streamtube.domain.channel.Channel;
import com.streamtube.domain.channel.ChannelRepository;
import com.streamtube.domain.shared.VideoExceptions.InvalidUploadSizeException;
import com.streamtube.domain.shared.VideoExceptions.UnsupportedVideoTypeException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a draft video and opens a multipart upload session: the file goes up in parts of
 * {@code upload.part-size-bytes} (S3 minimum 5 MiB), each with its own retriable presigned URL.
 * Same validation as the single-PUT initiate; the part size is fixed on the session so a later
 * config change never breaks an upload in flight.
 */
@Service
public class InitiateMultipartUploadUseCase {

  private static final int MAX_PARTS = 10_000; // S3 hard limit

  private final VideoRepository videoRepository;
  private final ChannelRepository channelRepository;
  private final StoragePort storage;
  private final UniqueSlugs uniqueSlugs;
  private final Clock clock;
  private final long maxUploadSizeBytes;
  private final long partSizeBytes;

  public InitiateMultipartUploadUseCase(
      VideoRepository videoRepository,
      ChannelRepository channelRepository,
      StoragePort storage,
      UniqueSlugs uniqueSlugs,
      Clock clock,
      @Value("${upload.max-size-bytes}") long maxUploadSizeBytes,
      @Value("${upload.part-size-bytes:8388608}") long partSizeBytes) {
    this.videoRepository = videoRepository;
    this.channelRepository = channelRepository;
    this.storage = storage;
    this.uniqueSlugs = uniqueSlugs;
    this.clock = clock;
    this.maxUploadSizeBytes = maxUploadSizeBytes;
    this.partSizeBytes = partSizeBytes;
  }

  @Transactional
  public InitiateMultipartResult execute(
      UUID userId, String title, long sizeBytes, String contentType) {
    if (sizeBytes <= 0 || sizeBytes > maxUploadSizeBytes) {
      throw new InvalidUploadSizeException();
    }
    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("video/")) {
      throw new UnsupportedVideoTypeException();
    }
    long totalParts = (sizeBytes + partSizeBytes - 1) / partSizeBytes;
    if (totalParts > MAX_PARTS) {
      throw new InvalidUploadSizeException();
    }

    Channel channel =
        channelRepository
            .findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("User has no channel"));

    String slug = uniqueSlugs.next();
    String storageKey = "videos/" + slug;
    Instant now = clock.instant();
    Video video = Video.initiate(UUID.randomUUID(), channel.id(), title, slug, storageKey, now);

    String uploadId = storage.createMultipartUpload(storageKey, contentType);
    video.beginMultipartUpload(uploadId, sizeBytes, partSizeBytes, now);
    videoRepository.save(video);

    return new InitiateMultipartResult(video.id(), video.slug(), partSizeBytes, (int) totalParts);
  }
}
