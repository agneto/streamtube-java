package com.streamtube.application.video;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard-deletes an owned video, any status. The row (and its social rows, via DB cascades — the
 * counters die with it) goes in this transaction; every storage artifact family is enqueued as a
 * prefix IN THE SAME transaction and wiped asynchronously by the worker's sweeper. Storage
 * objects are never deleted here: in-tx deletion leaks on rollback, post-commit-only leaks on
 * crash — the outbox is the durable middle. The one storage call is the multipart abort, which
 * must happen while the uploadId still exists (parts are invisible to prefix deletion).
 */
@Service
public class DeleteVideoUseCase {

  private final VideoOwnership ownership;
  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final StorageCleanupQueue cleanups;

  public DeleteVideoUseCase(
      VideoOwnership ownership,
      VideoRepository videoRepository,
      StoragePort storage,
      StorageCleanupQueue cleanups) {
    this.ownership = ownership;
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.cleanups = cleanups;
  }

  @Transactional
  public void execute(UUID videoId, UUID userId) {
    Video video = ownership.requireOwned(videoId, userId);
    if (video.hasActiveUpload()) {
      storage.abortMultipartUpload(video.storageKey(), video.uploadId());
    }
    VideoArtifacts.prefixesOf(video).forEach(cleanups::enqueue);
    videoRepository.delete(video);
  }
}
