---
kind: phase
name: phase-14-notifications-realtime
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Moving the notifications slice into shared persistence is the whole point (it is what lets the worker write), but its JPQL findFeed joins CommentEntity, which the worker does NOT map. Rewrite findFeed as a NATIVE query before the worker maps the repository, or the worker fails at bootstrap validating the join. Native SQL is not validated at startup — the findSubscriptionFeed precedent."
  - id: ADV-02
    text: "VIDEO_READY must be written in the SAME transaction as the READY status save, not in the non-transactional execute() body. Extract a @Transactional finishReady(video, ...) that does markReady-save + notification-create together; otherwise a crash between them leaves a READY video with no notification, or a notification for a video that rolled back. execute() itself stays non-transactional (long FFmpeg work must not pin a connection)."
  - id: ADV-03
    text: "READY is idempotent via execute()'s early isReady() return, so a redelivered message must not emit a second VIDEO_READY — keep the emit INSIDE the post-isReady() path. VIDEO_FAILED rides the existing @Transactional markFailed and its ifPresent, so a missing video emits nothing."
  - id: ADV-04
    text: "The recipient of VIDEO_READY/FAILED is a USER (the video owner), resolved from video.channelId() via ChannelRepository (the worker already maps it). actor_channel_id is NULL — it is a system notification, not a channel acting. Do not store the video's own channel as the actor."
  - id: ADV-05
    text: "Emit the real-time signal with a single DB trigger (AFTER INSERT ON notifications → pg_notify), NOT app-side pg_notify calls scattered across every write path. The trigger is the one choke-point every insert (API triggers, fan-out, worker) passes through; app-side calls are forgettable and would miss the fan-out or the worker. pg_notify inside the transaction is commit-gated by Postgres, so it never signals an uncommitted/rolled-back row."
  - id: ADV-06
    text: "The LISTEN connection must be a DEDICATED JDBC connection, never one borrowed from the Hikari pool — a LISTEN loop holds its connection for the process lifetime and would permanently starve the pool. Open it outside the pool, run getNotifications() on a background thread, and reconnect with backoff on failure."
  - id: ADV-07
    text: "SSE delivery is per-instance and best-effort. Each API replica LISTENs and serves only its own connected emitters; a NOTIFY reaches every replica, so every user is covered regardless of which replica they hit. Do NOT treat SSE as durable — the notifications table + unread-count stay the source of truth, and a reconnecting client refetches. Never gate a write on an emitter push succeeding."
  - id: ADV-08
    text: "EventSource cannot send Authorization, so the stream route accepts ?access_token=. Read the query token ONLY for /api/v1/notifications/stream (not globally) and validate it with the same JwtTokenService. Keep every other route header-only, so the query-token surface is one path with a short-lived token."
  - id: ADV-09
    text: "Register emitter cleanup on completion AND timeout AND error before returning it, and make registry.push best-effort (complete + drop a dead emitter, swallow IOException). A leaked emitter set grows unbounded per instance; a push that throws must not break the listener loop for other users."
  - id: ADV-10
    text: "Keep the SSE payload to { type, unreadCount } — no notification content over the long-lived channel. The unread count is a cheap partial-index query (Phase 13 idx_notifications_recipient_unread); carry the triggering type in the NOTIFY payload (recipientUserId|type, well under 8000 bytes) so the listener does not re-read the row."
---

# Phase 14 — Validation

## Decisions coverage

Builds only on surfaces already shipped: the Phase 13 `notifications` table and domain, the worker's
`ProcessVideoUseCase` (READY/FAILED transitions), the shared `ChannelRepository`, and the existing
JWT verification. The two new events have a defined recipient (user), actor (none), reference
(video), and transaction boundary. Real-time delivery is fully specified: DB trigger → `pg_notify`
→ per-instance `LISTEN` → emitter registry → SSE, with auth, payload, and lifecycle all decided. The
persistence-boundary reversal (API-only → shared) is explicit and is the documented prerequisite
Phase 13 recorded.

## Dependency gaps

None. `varchar(32)` already holds the two new type values (no column change). The worker already
maps `ChannelRepository` and `NotificationEntity` (after the move). `pg_notify` / `LISTEN` are
built-in Postgres (the compose DB is `postgres:17-alpine`). The partial unread index exists. The
only new schema is the V14 trigger.

## Risks & mitigations

- **Native findFeed mapping** (snake_case → record): use `@SqlResultSetMapping(@ConstructorResult)`
  targeting `NotificationFeedRow`, not alias-guessing — validated and explicit (ADV-01).
- **SSE integration test flakiness:** drive it through MockMvc async dispatch against a Testcontainers
  Postgres, awaiting the first event with a bounded timeout; assert `unreadCount`, not timing.
- **Listener connection leaks / restarts:** dedicated connection with `DisposableBean` close and
  reconnect-with-backoff (ADV-06); verified by a listener lifecycle unit test.

## Verdict

**clean** — ready to implement.
