package com.streamtube.worker.listener;

import com.streamtube.application.video.ProcessStorageCleanupsUseCase;
import com.streamtube.application.video.PurgeStaleUploadsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The lifecycle sweeper: retires stale PENDING_UPLOAD drafts and drains the storage-cleanup
 * outbox. Worker-only (API replicas behind a load balancer would duplicate it); with multiple
 * workers a double run is harmless — every operation is idempotent.
 */
@Component
public class CleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

  private final PurgeStaleUploadsUseCase purgeStaleUploads;
  private final ProcessStorageCleanupsUseCase processStorageCleanups;

  public CleanupScheduler(
      PurgeStaleUploadsUseCase purgeStaleUploads,
      ProcessStorageCleanupsUseCase processStorageCleanups) {
    this.purgeStaleUploads = purgeStaleUploads;
    this.processStorageCleanups = processStorageCleanups;
  }

  @Scheduled(cron = "${cleanup.interval-cron:0 */15 * * * *}")
  public void run() {
    try {
      int purged = purgeStaleUploads.execute();
      int cleaned = processStorageCleanups.execute();
      if (purged > 0 || cleaned > 0) {
        log.info("Cleanup sweep: {} stale drafts purged, {} storage prefixes wiped",
            purged, cleaned);
      }
    } catch (Exception e) {
      // Next tick retries whatever is left (the queue only shrinks on confirmed wipes).
      log.error("Cleanup sweep failed; will retry on the next tick", e);
    }
  }
}
