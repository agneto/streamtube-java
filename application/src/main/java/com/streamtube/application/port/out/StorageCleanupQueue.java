package com.streamtube.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Durable outbox for storage deletions. Prefixes are enqueued in the SAME transaction as the row
 * changes that orphan them (deleting storage inside the tx leaks on rollback; only after commit
 * leaks on crash) and drained asynchronously by the worker's sweeper — at-least-once, idempotent.
 */
public interface StorageCleanupQueue {

  void enqueue(String prefix);

  /** Oldest first, bounded batch. */
  List<PendingCleanup> due(int limit);

  /** Only after the storage confirmed the wipe — failures stay queued and retry next tick. */
  void remove(UUID id);

  record PendingCleanup(UUID id, String prefix) {}
}
