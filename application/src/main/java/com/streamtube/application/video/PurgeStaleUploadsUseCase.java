package com.streamtube.application.video;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StoragePort;
import com.streamtube.domain.video.Video;
import com.streamtube.domain.video.VideoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sweeper half 1: retires PENDING_UPLOAD drafts older than the retention window (initiate was
 * called, bytes never confirmed). Same mechanics as user deletion: abort any session, enqueue the
 * artifact prefixes, drop the row. Batched and rerun-safe.
 *
 * <p>Not a component: worker-only, wired in {@code WorkerBeans} (the ProcessVideoUseCase
 * precedent).
 */
public class PurgeStaleUploadsUseCase {

  private static final int BATCH = 100;

  private final VideoRepository videoRepository;
  private final StoragePort storage;
  private final StorageCleanupQueue cleanups;
  private final Clock clock;
  private final int staleUploadDays;

  public PurgeStaleUploadsUseCase(
      VideoRepository videoRepository,
      StoragePort storage,
      StorageCleanupQueue cleanups,
      Clock clock,
      int staleUploadDays) {
    this.videoRepository = videoRepository;
    this.storage = storage;
    this.cleanups = cleanups;
    this.clock = clock;
    this.staleUploadDays = staleUploadDays;
  }

  @Transactional
  public int execute() {
    Instant cutoff = clock.instant().minus(Duration.ofDays(staleUploadDays));
    List<Video> stale = videoRepository.findStalePendingUploads(cutoff, BATCH);
    for (Video video : stale) {
      if (video.hasActiveUpload()) {
        storage.abortMultipartUpload(video.storageKey(), video.uploadId());
      }
      VideoArtifacts.prefixesOf(video).forEach(cleanups::enqueue);
      videoRepository.delete(video);
    }
    return stale.size();
  }
}
