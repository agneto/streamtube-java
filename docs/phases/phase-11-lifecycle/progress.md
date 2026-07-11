# phase-11-lifecycle — Progress

**Status:** not started
**SIs:** 0/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-11.1 | Domain/ports (delete, stale query, StorageCleanupQueue) | pending | |
| SI-11.2 | Flyway V12 (storage_cleanups) | pending | |
| SI-11.3 | Persistence (delete, stale, cleanup queue — entity visível ao worker) | pending | |
| SI-11.4 | Storage (deleteObjectsByPrefix paginado/batched + fake) | pending | |
| SI-11.5 | Use cases + wiring (DELETE route, sweeper agendado, listener drop) | pending | |
| SI-11.6 | Tests (unit + E2E + smoke com objetos sumindo do MinIO) | pending | |
| SI-11.7 | Docs + DoD | pending | |

## Notes

- Fourth post-roadmap improvement: closes the video lifecycle (deletion deferred since Phase 04 +
  the last §6 item) with one shared mechanism — a durable outbox cleanup queue drained by a
  worker-scheduled sweeper.
- Deletion never touches storage in the request transaction; whatever a crash leaves behind, the
  queue still holds (at-least-once, idempotent).
- Stale `PENDING_UPLOAD` drafts (default 7 days) are retired by the same sweeper.
