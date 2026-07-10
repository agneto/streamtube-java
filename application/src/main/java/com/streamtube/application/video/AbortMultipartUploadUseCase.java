package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.shared.VideoExceptions.NoActiveUploadException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aborts the multipart session: every uploaded part is discarded in storage and the session is
 * cleared. The video stays a {@code PENDING_UPLOAD} draft (record born at initiate, as always).
 */
@Service
public class AbortMultipartUploadUseCase {

  private final VideoOwnership ownership;
  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final Clock clock;

  public AbortMultipartUploadUseCase(
      VideoOwnership ownership,
      VideoRepository videoRepository,
      StoragePort storage,
      Clock clock) {
    this.ownership = ownership;
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID videoId, UUID userId) {
    Video video = ownership.requireOwned(videoId, userId);
    if (!video.hasActiveUpload()) {
      throw new NoActiveUploadException();
    }
    storage.abortMultipartUpload(video.storageKey(), video.uploadId());
    video.clearUploadSession(clock.instant());
    videoRepository.save(video);
  }
}
