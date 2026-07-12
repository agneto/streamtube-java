# phase-11-lifecycle — Progress

**Status:** completed
**SIs:** 7/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-11.1 | Domain/ports (delete, stale query, StorageCleanupQueue) | done | no entity changes; prefix families centralized in VideoArtifacts so deletion and purge can never drift |
| SI-11.2 | Flyway V12 (storage_cleanups) | done | applied by Testcontainers E2E |
| SI-11.3 | Persistence (delete, stale, cleanup queue — entity visível ao worker) | done | StorageCleanupEntity in the shared entity package (ADV-09) |
| SI-11.4 | Storage (deleteObjectsByPrefix paginado/batched + fake) | done | ListObjectsV2 + DeleteObjects iterated to exhaustion (ADV-06) |
| SI-11.5 | Use cases + wiring (DELETE route, sweeper agendado, listener drop) | done | worker-only beans (ProcessVideoUseCase precedent); @EnableScheduling; dev compose sweeps every 20s |
| SI-11.6 | Tests (unit + E2E + smoke) | done | DeleteVideoUseCaseTest (exact prefixes, abort-before-delete order, no in-tx storage), LifecycleSweeperUseCasesTest (cutoff math, remove-only-after-success), listener drop; E2E full deletion + stale purge |
| SI-11.7 | Docs + DoD | done | system-design §6 (last item flipped), deploy.md §6, GUIA, Postman "13. Apagar vídeo" |

## Notes

- Fourth post-roadmap improvement: closes the video lifecycle (deletion deferred since Phase 04 +
  the last §6 item) with one shared mechanism — a durable outbox cleanup queue drained by a
  worker-scheduled sweeper.
- Deletion never touches storage in the request transaction; whatever a crash leaves behind, the
  queue still holds (at-least-once, idempotent).
- Stale `PENDING_UPLOAD` drafts (default 7 days) are retired by the same sweeper.
