package com.streamtube.application.video;

import com.streamtube.application.port.out.StoragePort;
import com.streamtube.application.port.out.UploadedPart;
import com.streamtube.domain.shared.VideoExceptions.NoActiveUploadException;
import com.streamtube.domain.shared.VideoExceptions.UploadNotCompletedException;
import com.streamtube.domain.shared.VideoExceptions.VideoStatusConflictException;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoStatus;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the multipart object server-side: the part list (with ETags) comes from the storage
 * itself, never from the client. After assembly, the object's size must equal the declared
 * {@code sizeBytes} — a mismatch removes the object and fails, so a short upload can never become
 * a consumable video. Then the same QUEUED + publish-after-commit tail as the single PUT.
 */
@Service
public class CompleteMultipartUploadUseCase {

  private final VideoOwnership ownership;
  private final StoragePort storage;
  private final QueueForProcessing queueForProcessing;
  private final Clock clock;

  public CompleteMultipartUploadUseCase(
      VideoOwnership ownership,
      StoragePort storage,
      QueueForProcessing queueForProcessing,
      Clock clock) {
    this.ownership = ownership;
    this.storage = storage;
    this.queueForProcessing = queueForProcessing;
    this.clock = clock;
  }

  @Transactional
  public void execute(UUID videoId, UUID userId) {
    Video video = ownership.requireOwned(videoId, userId);
    if (video.status() != VideoStatus.PENDING_UPLOAD) {
      throw new VideoStatusConflictException();
    }
    if (!video.hasActiveUpload()) {
      throw new NoActiveUploadException();
    }

    List<UploadedPart> parts = storage.listUploadedParts(video.storageKey(), video.uploadId());
    if (parts.size() != video.totalParts()) {
      throw new UploadNotCompletedException();
    }
    storage.completeMultipartUpload(video.storageKey(), video.uploadId(), parts);

    long actualSize = storage.objectSizeBytes(video.storageKey());
    if (actualSize != video.uploadSizeBytes()) {
      storage.deleteObject(video.storageKey());
      throw new UploadNotCompletedException();
    }

    video.clearUploadSession(clock.instant());
    queueForProcessing.execute(video, clock.instant());
  }
}
