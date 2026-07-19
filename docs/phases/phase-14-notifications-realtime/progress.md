# phase-14-notifications-realtime — Progress

**Status:** planned
**SIs:** 0/7 completed

| SI | Description | Status | Notes |
|----|-------------|--------|-------|
| SI-14.1 | Move notifications slice to shared persistence; findFeed → native | todo | reverses Phase 13 API-only placement (ADV-01); @ConstructorResult mapping |
| SI-14.2 | Worker events VIDEO_READY/VIDEO_FAILED (+ factories, WorkerBeans wiring) | todo | READY in a @Transactional finishReady; FAILED in existing markFailed (ADV-02/03/04) |
| SI-14.3 | Migration V14 — pg_notify AFTER INSERT trigger | todo | single choke-point; commit-gated NOTIFY (ADV-05) |
| SI-14.4 | SseEmitterRegistry + PostgresNotificationListener | todo | dedicated LISTEN connection, backoff, best-effort push (ADV-06/07/09) |
| SI-14.5 | GET /notifications/stream + query-param token for this route | todo | initial unread snapshot; ?access_token= only here (ADV-08) |
| SI-14.6 | Tests (worker unit, registry/listener, SSE integration, extend Phase 13 E2E) | todo | assert SSE event { type, unreadCount } after a trigger (ADV-10) |
| SI-14.7 | Docs + DoD (system-design, GUIA, Postman, progress) | todo | real-time delivered; LISTEN/NOTIFY topology |

## Notes

- Seventh post-roadmap improvement; part 2 of notifications. Completes the two evolutions Phase 13
  deferred on purpose (`phase-13-notifications/context.md` out-of-scope): worker-sourced
  VIDEO_READY/FAILED and real-time push.
- The persistence-boundary reversal (API-only slice → shared) is the flagged prerequisite from
  Phase 13 ADV-07, not silent drift.
- No new table and no broker: durable state stays in `notifications`; `pg_notify` is transient
  signalling emitted by one DB trigger and carried per-instance over `LISTEN`.
- Baseline at planning: 242 tests, migrations V1–V13, version 1.6.0.
