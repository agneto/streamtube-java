# Phase 11 — Video Lifecycle: Deletion & Orphan Cleanup (plan)

## Objective

`DELETE /videos/{id}` removes the row (and cascaded social rows) transactionally and every
storage artifact asynchronously via a durable cleanup queue; a worker-scheduled sweeper drains
that queue and retires stale `PENDING_UPLOAD` drafts — no leak path left without an owner.

---

## Technical Specifications

### Data Model — migration V12

```sql
CREATE TABLE storage_cleanups (
    id         uuid PRIMARY KEY,
    prefix     varchar(600) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_storage_cleanups_created ON storage_cleanups (created_at);
```

Videos table: no change (deletion is a row op; FKs from V8 already cascade).

### Ports

```java
// VideoRepository (existing)
void delete(Video video);
List<Video> findStalePendingUploads(Instant cutoff, int limit);   // oldest first

// application/port/out — technical housekeeping, not a domain concept
public interface StorageCleanupQueue {
  void enqueue(String prefix);                 // same tx as the caller's row changes
  List<PendingCleanup> due(int limit);         // oldest first
  void remove(UUID id);                        // only after storage confirmed the wipe
  record PendingCleanup(UUID id, String prefix) {}
}

// StoragePort (existing)
void deleteObjectsByPrefix(String prefix);     // ListObjectsV2 paginated + DeleteObjects ≤1000
```

### Use cases

- **`DeleteVideoUseCase`** (API, `@Transactional`): `VideoOwnership.requireOwned` → if
  `hasActiveUpload()` abort the multipart session (before the row vanishes with the uploadId) →
  enqueue `videos/{slug}`, `thumbnails/{slug}`, `hls/{slug}/` → `videoRepository.delete(video)`.
  Storage is never touched in the transaction.
- **`PurgeStaleUploadsUseCase`** (worker): for each stale `PENDING_UPLOAD`
  (`created_at < now - cleanup.stale-upload-days`, batch 100): abort session if any, enqueue the
  same three prefixes, delete the row. Rerun-safe.
- **`ProcessStorageCleanupsUseCase`** (worker): drain `due(100)`; for each,
  `deleteObjectsByPrefix` then `remove(id)` — removal only after success, so failures retry on
  the next tick (at-least-once; deleting absent keys is a no-op).
- **Listener refinement:** `VideoProcessingListener` treats `VideoNotFoundException` as
  "video deleted meanwhile" — ack and drop, no retry, no DLQ.

### Worker scheduling

`@EnableScheduling` on the worker app; both jobs on one cron (`cleanup.interval-cron`, default
`0 */15 * * * *`). Config: `cleanup.stale-upload-days` (`CLEANUP_STALE_UPLOAD_DAYS`, default 7).
Multiple workers may double-run — harmless by idempotency (documented in deploy.md).

### Contracts

- `DELETE /api/v1/videos/{id}` → 204 (owner), 403 (not owner), 404 (unknown). Idempotence: a
  second DELETE is a 404 — the resource is gone, which is the truthful answer.
- After deletion: every read of the slug is 404; comments/reactions are gone (FK cascade);
  storage artifacts disappear within one sweeper tick.

---

## Sub-issues

- **SI-11.1 — Domain/ports:** `VideoRepository.delete`/`findStalePendingUploads`;
  `StorageCleanupQueue` port. No entity changes.
- **SI-11.2 — Flyway V12:** `storage_cleanups`.
- **SI-11.3 — Persistence:** delete + stale query on the adapter; `StorageCleanupEntity` (in the
  shared entity package — the worker drains the queue, so its persistence unit must map it) +
  repo + adapter.
- **SI-11.4 — Storage:** `deleteObjectsByPrefix` on `StoragePort`, S3 adapter (paginated listing,
  batched DeleteObjects) and the shared test `FakeStorage`.
- **SI-11.5 — Use cases + wiring:** `DeleteVideoUseCase` + `DELETE` route (no security change —
  authenticated by default); worker: purge/drain use cases, `@EnableScheduling`, `WorkerBeans`,
  listener drops `VideoNotFoundException`; config `cleanup.*`.
- **SI-11.6 — Tests:** unit (enqueue set exact incl. multipart abort ordering, stale rules and
  cutoff, drain removes only on success, listener drop, second delete 404) + E2E (delete video
  with comments/reactions/HLS keys → 404 everywhere, queue drained via use case beans invoked
  directly — the scheduler itself is not E2E-testable —, stale draft purged with backdated
  `created_at`) + compose smoke (upload → publish → comment → delete → 404 + objects actually
  gone from MinIO incl. the HLS ladder; orphaned initiate purged after forcing the job).
- **SI-11.7 — Docs + DoD:** system-design §6 flips the last item (+ §3.5 orphan note now points
  at the sweeper), deploy.md cleanup section (retention, cadence, multi-worker note, manual
  bucket-diff procedure), GUIA-DE-USO; progress.md; `./gradlew build` verde.

## Dependency Map

```
SI-11.1 ── SI-11.2 ── SI-11.3 ── SI-11.4 ── SI-11.5 ── SI-11.6 ── SI-11.7
```

## Deliverables

1. `docs/phases/phase-11-lifecycle/` — context, plan, validation, progress
2. `DELETE /api/v1/videos/{id}` com cascata social e limpeza total do storage
3. Fila durável de limpeza (outbox) + sweeper agendado no worker
4. Rascunhos `PENDING_UPLOAD` velhos retirados automaticamente
5. Listener sem ruído de DLQ para vídeos deletados em processamento
6. Unit + Testcontainers E2E + smoke com verificação de objetos sumindo do MinIO
