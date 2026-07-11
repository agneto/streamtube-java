---
kind: phase
name: phase-11-lifecycle
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Cleanup prefixes are enqueued in the SAME transaction as the row delete. Calling storage inside the tx leaks objects on rollback; calling it only after commit leaks on crash. The outbox table is the durable middle — never shortcut it."
  - id: ADV-02
    text: "The prefix set must cover every artifact family: videos/{slug}, thumbnails/{slug} (both .jpg and -custom), hls/{slug}/. A missed family becomes a permanent orphan the sweeper cannot see. Assert the exact enqueued set in unit tests and object absence in the smoke."
  - id: ADV-03
    text: "Abort an active multipart session BEFORE deleting the row: the uploadId lives on the video and is unrecoverable afterwards, and multipart parts are invisible to prefix deletion (only the bucket lifecycle rule would ever reclaim them)."
  - id: ADV-04
    text: "Deleting a QUEUED/PROCESSING video makes the worker's findById come up empty: the listener must ack-and-drop VideoNotFoundException instead of retry×3 → DLQ → markFailed — deletion is not a processing failure."
  - id: ADV-05
    text: "The sweeper removes a queue entry only AFTER deleteObjectsByPrefix succeeded (at-least-once). Removing first turns any storage hiccup into a silent permanent leak."
  - id: ADV-06
    text: "DeleteObjects caps at 1000 keys and ListObjectsV2 paginates — an HLS ladder of a long video exceeds both. Iterate to exhaustion (the Phase 08 ListParts lesson)."
  - id: ADV-07
    text: "Prefix collision safety rests on slugs being fixed-length (11 chars): thumbnails/{slugA} can never prefix-match thumbnails/{slugB}. If the slug generator ever changes length, this assumption breaks — record it next to the generator."
  - id: ADV-08
    text: "The scheduler lives in the WORKER only. Multiple workers may double-run the jobs: every operation must stay idempotent (delete absent = no-op, row deletes race benignly). Do not add locking complexity — document the property instead."
  - id: ADV-09
    text: "storage_cleanups is drained by the worker: its JPA entity goes in the shared persistence.entity package (NOT infrastructure.social), or the worker's persistence unit cannot see it."
---

# Phase 11 — Validation

## Decisions coverage

Pays the two remaining documented debts (Phase 04's deferred deletion; §6 item 5) with one
mechanism. Consistent with every standing decision: transactions stay short (storage work outside
them), the record-born-at-initiate model gains its symmetric end, social counters need no math
(FK cascade kills the rows that carried them). New conventions in context.md: outbox cleanup
queue, prefix families, hard-delete semantics, worker-scheduled sweeper. No undecided topic
blocks implementation.

## Dependency gaps

None. Touches surfaces from Phases 03/04 (upload/read paths), 06 (cascading social rows), 08
(multipart abort), 09 (HLS prefix) — all shipped through v1.3.0.

## Verdict

**clean** — ready to implement.
