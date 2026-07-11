# Phase 11 — Video Lifecycle: Deletion & Orphan Cleanup (context)

## Goal

Close the video lifecycle end to end, paying the two remaining documented debts at once:

- **Video deletion** — explicitly deferred since Phase 04 ("storage-cleanup semantics deserve
  their own slice"). Now a video owns artifacts in four storage families (original, thumbnails,
  HLS ladder, possibly an open multipart session) plus cascading social rows.
- **Orphan cleanup** — the last item of `system-design.md` §6: stale `PENDING_UPLOAD` drafts
  (initiate called, bytes never confirmed) and leftover storage objects.

The two features share one mechanism: a durable **cleanup queue** (outbox-style table). Deletion
enqueues storage prefixes in the same transaction that removes the row; a scheduled sweeper in
the worker drains the queue and also retires stale drafts. Neither side needs storage+DB
atomicity — whatever a crash leaves behind, the queue still holds.

## Endpoints to add

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| DELETE | `/api/v1/videos/{id}` | owner | Hard-delete the video: row (+ cascaded social rows) now, storage artifacts asynchronously |

No other endpoint changes. The sweeper is not an endpoint — it is a worker-scheduled job.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Deletion semantics | **Hard delete**, any status. The DB row goes in the request transaction (authorization + FK cascades kill comments/reactions in the same commit — counters die with the row, no counter math). Storage artifacts are enqueued as prefixes in the same transaction and deleted asynchronously. |
| Cleanup queue | `storage_cleanups (id, prefix, created_at)` — a transactional outbox for storage work. Direct storage calls inside the tx would leak on rollback; after-commit-only calls would leak on crash. The table is the durable middle: at-least-once, idempotent (deleting absent objects is a no-op). |
| Prefixes per video | `videos/{slug}` (original), `thumbnails/{slug}` (covers `.jpg` and `-custom`), `hls/{slug}/` (whole ladder). Slugs are fixed 11 chars, so no slug is a prefix of another — prefix matching is collision-safe. An open multipart session is aborted **before** the row delete (the uploadId is lost afterwards). |
| Deleting mid-processing | Allowed. The worker learns the row vanished (`VideoNotFoundException`); the listener now **drops** that case without retry — a deleted video must not generate retry×3 + DLQ noise. |
| Stale drafts | `PENDING_UPLOAD` older than `cleanup.stale-upload-days` (default 7): the sweeper aborts any multipart session, enqueues the video's prefixes, deletes the row. Same threshold family as the bucket lifecycle rule from Phase 08. |
| Sweeper placement | Scheduled in the **worker** (`@EnableScheduling`; API replicas would duplicate it under load balancing, and background work is the worker's job). Batched (100/run), oldest first, rerun-safe; with multiple workers a double run is harmless because every operation is idempotent (documented). |
| Unregistered-object bucket scan | **Out of scope.** Every leak path now lands in the queue (deletion, stale drafts) or in the bucket lifecycle rule (multipart). A full bucket↔DB diff stays a manual ops procedure. |

## Lessons carried over

- Transaction boundaries: row changes commit fast; storage work happens outside (Phase 03
  worker pattern, AfterCommitExecutor philosophy — upgraded to a durable table where loss matters).
- DeleteObjects batches at 1000 keys and listings paginate — iterate to exhaustion (the
  ListParts lesson from Phase 08).
- Worker-only wiring for worker-only jobs (`WorkerBeans` precedent).
- Ops notes ship with the feature (retention default, sweeper cadence) in `deploy.md`.

## Out of scope

- Soft delete / trash / undo, moderation (owner-only deletion), channel/account deletion.
- Notifications about removed content.
- Bucket-wide reconciliation scans (manual procedure documented instead).
- Any change to publication, processing or social semantics.
