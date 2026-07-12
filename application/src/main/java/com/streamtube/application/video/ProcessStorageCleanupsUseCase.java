package com.streamtube.application.video;

import com.streamtube.application.port.out.StorageCleanupQueue;
import com.streamtube.application.port.out.StorageCleanupQueue.PendingCleanup;
import com.streamtube.application.port.out.StoragePort;
import java.util.List;

/**
 * Sweeper half 2: drains the cleanup outbox. An entry only leaves the queue AFTER the storage
 * confirmed the wipe — a failure stops the batch and everything left retries next tick
 * (at-least-once; deleting absent objects is a no-op, so double runs are harmless).
 *
 * <p>Deliberately not transactional as a whole: storage calls are slow and each removal commits
 * on its own, so a crash mid-batch loses nothing.
 *
 * <p>Not a component: worker-only, wired in {@code WorkerBeans}.
 */
public class ProcessStorageCleanupsUseCase {

  private static final int BATCH = 100;

  private final StorageCleanupQueue cleanups;
  private final StoragePort storage;

  public ProcessStorageCleanupsUseCase(StorageCleanupQueue cleanups, StoragePort storage) {
    this.cleanups = cleanups;
    this.storage = storage;
  }

  public int execute() {
    List<PendingCleanup> due = cleanups.due(BATCH);
    for (PendingCleanup cleanup : due) {
      storage.deleteObjectsByPrefix(cleanup.prefix());
      cleanups.remove(cleanup.id());
    }
    return due.size();
  }
}
