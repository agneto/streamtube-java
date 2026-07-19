# Phase 14 — Notifications, part 2: worker events + real-time delivery (context)

## Goal

Close the two evolutions that Phase 13 deliberately deferred, turning the poll-based feed into a
live one and giving the **worker** a voice in it:

1. **Worker-sourced events** — when the processing pipeline finishes, the video owner is told:
   `VIDEO_READY` (their upload is playable) or `VIDEO_FAILED` (processing gave up after retries).
   These originate in `bootstrap-worker`, which is exactly why Phase 13 left them out: the
   notifications slice was API-only. Phase 14 moves the slice into **shared persistence** so both
   bootstraps can write to it.
2. **Real-time delivery** — the badge/feed updates without polling. A browser opens an SSE stream;
   any notification insert (from any API instance **or** the worker) reaches the right user's open
   streams. The cross-process fan-out rides **Postgres `LISTEN/NOTIFY`**, emitted by a single DB
   trigger on `notifications` — no message broker, no shared cache.

No new business events beyond the two worker ones; no preferences/muting (still out of scope). This
phase is about **completeness and liveness** of what Phase 13 already models.

## Events added

| Type | Recipient | Actor | Fires when | Source |
|------|-----------|-------|-----------|--------|
| `VIDEO_READY` | video owner | — (system) | `ProcessVideoUseCase` reaches READY (first time) | worker |
| `VIDEO_FAILED` | video owner | — (system) | `ProcessVideoUseCase.markFailed` after retries exhausted | worker |

Both reference the video (`video_id`); neither has an actor channel (it is the platform telling you
about your own upload). They are **single-recipient inserts**, written in the same transaction as
the status write that caused them (READY/ERROR) — a notification that outlived a rolled-back status
change would be a lie.

## Endpoints added

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/notifications/stream` | authenticated | Server-Sent Events: pushes an event per new notification for the caller (payload = `{ type, unreadCount }`) |

The four Phase 13 routes are unchanged. The stream is additive: a client that never opens it keeps
working exactly as today (poll `unread-count`).

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Persistence boundary | The notifications slice **moves from `infrastructure.notification` (API-only) to shared `infrastructure.persistence.{entity,repository,adapter}`**, so the worker's persistence unit maps it too. This reverses Phase 13's API-only placement on purpose — it was the flagged prerequisite for worker-sourced events (Phase 13 ADV-07). |
| Feed query | `findFeed` becomes a **native** query. The worker maps the shared repository, and its JPQL form joined `CommentEntity` (still API-only) — native SQL isn't validated at the worker's bootstrap (the `findSubscriptionFeed` precedent), so it joins `comments`/`channels`/`videos` by table and maps the result to `NotificationFeedRow` via a constructor result mapping. |
| Worker recipient resolution | The worker already maps `ChannelRepository` (shared `infrastructure.persistence.*`), so it resolves the video owner's `user_id` from `video.channelId()` with no new port. |
| Transaction boundary (worker) | `VIDEO_READY` is written **in the same short transaction** as the READY status save (a new `@Transactional` finish step), so `execute()` stays non-transactional over the long FFmpeg work. `VIDEO_FAILED` joins the already-`@Transactional` `markFailed`. Idempotent: `execute()` returns early when `isReady()`, so a redelivery never emits a duplicate READY. |
| Real-time signal | A **single Postgres trigger** `AFTER INSERT ON notifications` calls `pg_notify('streamtube_notification', NEW.recipient_user_id::text)`. Every insert path (API triggers, NEW_VIDEO fan-out, worker) emits automatically — impossible to forget — and Postgres defers/rolls-back the NOTIFY with the transaction, so it fires only for committed rows (ADV-01 holds for free). |
| Cross-instance delivery | Each API instance holds **one dedicated JDBC connection** running `LISTEN streamtube_notification` on a background loop (outside the Hikari pool). On a NOTIFY it looks up local SSE emitters for that `user_id` and pushes. Multiple instances each get the NOTIFY; each serves only the clients connected to it. |
| SSE payload | Minimal: `{ type, unreadCount }`. The unread count lets the badge update with zero extra round-trips; `type` lets the client decide whether to refetch the open panel. No notification content travels the long-lived channel (privacy + smaller surface). |
| SSE authentication | `EventSource` cannot set an `Authorization` header, so the stream route also accepts the access token as `?access_token=` (validated by the same `JwtTokenService`, only on this path). Trade-off: query tokens can land in access logs — mitigated by the short-lived access token and scoping the query-param read to this single route. |
| No broker, no schema for state | Real-time needs **no new table and no message broker** — `pg_notify` is transient signalling, the durable state is still the `notifications` rows. The only new migration is the trigger (V14). |

## Lessons carried over

- DB-native mechanisms as the single choke-point (Phase 11 FK cascades; Phase 13 partial index) —
  here a **DB trigger** guarantees every insert signals, with no app-side bookkeeping.
- Notifications share the fate of their trigger's transaction (Phase 13 ADV-01) — `pg_notify`
  inside the transaction inherits Postgres's commit-gated delivery, so liveness never leaks
  uncommitted rows.
- Native SQL for cross-slice joins that must not bind the worker's bootstrap (Phase 06
  `findSubscriptionFeed`) — reused for `findFeed` now that the repository is shared.
- The worker maps only what it needs; here that set legitimately grows to include `notifications`
  (documented reversal of Phase 13's boundary, not silent drift).

## Out of scope

- **Preferences / muting / per-type opt-out** and **email/mobile push** — unchanged from Phase 13.
- **Reaction notifications** and **aggregation/grouping** — unchanged from Phase 13.
- **WebSockets** — SSE is the right shape for a one-way server→client badge stream; bidirectional
  transport would be over-engineering.
- **Redis / external pub-sub** — `LISTEN/NOTIFY` covers cross-instance fan-out at this scale; a
  broker is only warranted past Postgres's connection ceiling, and is a clean later swap behind the
  same registry.
- **Replaying missed events** — SSE is best-effort live; the durable feed + `unread-count` remain
  the source of truth, so a client that was offline simply refetches on reconnect.
