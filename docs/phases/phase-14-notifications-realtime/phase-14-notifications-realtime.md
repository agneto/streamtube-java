# Phase 14 — Notifications, part 2: worker events + real-time delivery (plan)

## Objective

Give the worker a voice in the notification feed (`VIDEO_READY` / `VIDEO_FAILED`) and make the feed
live: an SSE stream pushes an event to the right user whenever a notification is inserted anywhere,
carried across API instances by a single Postgres `LISTEN/NOTIFY` trigger. One new migration (the
trigger), no new table, no broker.

---

## Technical Specifications

### 1. Move the notifications slice to shared persistence

Phase 13 put the slice in API-only `infrastructure.notification`. Relocate:

- `NotificationEntity` → `infrastructure.persistence.entity`
- `NotificationJpaRepository` → `infrastructure.persistence.repository`
- `NotificationRepositoryAdapter` → `infrastructure.persistence.adapter`

Delete the extra `infrastructure.notification` entries from the API's `@EntityScan` /
`@EnableJpaRepositories` (the slice is now under the packages both bootstraps already scan). The
worker maps it automatically.

**`findFeed` becomes native.** Its JPQL form joined `CommentEntity` (API-only); the worker would
fail to validate that at bootstrap. Rewrite as a native query joining `channels` / `videos` /
`comments` by table (newest-first, `countQuery` for the page), mapped to `NotificationFeedRow` via a
`@SqlResultSetMapping(@ConstructorResult ...)`. The other methods are worker-safe as-is: `create`
(save), `fanOutNewVideo` (already native), `countByRecipientUserIdAndReadAtIsNull` (derived),
`markRead` / `markAllRead` (JPQL update on `NotificationEntity` only — no joins). The domain port
`NotificationRepository` is unchanged.

### 2. Worker-sourced events

```java
enum NotificationType { NEW_SUBSCRIBER, VIDEO_COMMENT, COMMENT_REPLY, NEW_VIDEO,
                        VIDEO_READY, VIDEO_FAILED }   // + two values

// factories (recipient = video owner's user; no actor channel)
Notification.videoReady (UUID id, UUID recipientUserId, UUID videoId, Instant now);
Notification.videoFailed(UUID id, UUID recipientUserId, UUID videoId, Instant now);
```

`type` is `varchar(32)` — the new values need **no schema change**.

`ProcessVideoUseCase` (worker, wired in `WorkerBeans`) gains a `NotificationRepository` and the
already-available `ChannelRepository`:

- **READY:** extract the final `video.markReady(...); videoRepository.save(video)` into a new
  `@Transactional finishReady(...)` that also resolves the owner (`channelRepository.findByIds(
  List.of(video.channelId()))` → `userId`) and `notifications.create(Notification.videoReady(...))`
  — one transaction, so the notification and the READY row commit together. `execute()` stays
  non-transactional (the long FFmpeg work holds no connection); its early `isReady()` return keeps
  READY idempotent on redelivery (no duplicate notification).
- **FAILED:** inside the existing `@Transactional markFailed(...)`, after `video.markError(...)`,
  `notifications.create(Notification.videoFailed(...))` in the same `ifPresent` block.

No actor channel, no fan-out. The recipient is a single user.

### 3. Migration V14 — NOTIFY trigger

```sql
CREATE FUNCTION notify_notification() RETURNS trigger AS $$
BEGIN
  PERFORM pg_notify('streamtube_notification', NEW.recipient_user_id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER notifications_notify
  AFTER INSERT ON notifications
  FOR EACH ROW EXECUTE FUNCTION notify_notification();
```

Every insert path — API triggers, the set-based `NEW_VIDEO` fan-out (one NOTIFY per inserted row),
and the worker — signals automatically. Postgres holds the NOTIFY until the inserting transaction
commits and drops it on rollback, so liveness inherits Phase 13's ADV-01 for free.

### 4. Real-time delivery (API side)

- **`SseEmitterRegistry`** (`infrastructure` or `api`, per-instance): `Map<UUID, Set<SseEmitter>>`
  keyed by `recipientUserId`. `add(userId, emitter)` (registers completion/timeout removal),
  `remove(...)`, `push(userId, event)` (best-effort; a dead emitter is completed and dropped).
- **`PostgresNotificationListener`** (`infrastructure`, one per instance): opens a **dedicated**
  JDBC connection (not from the Hikari pool), issues `LISTEN streamtube_notification`, and runs a
  background loop calling `PGConnection.getNotifications(timeout)`. Each notification's payload is
  the `recipientUserId`; it asks the registry to `push` an event to that user's emitters. On the
  push it computes the fresh `unreadCount` (a cheap partial-index query) and the triggering `type`
  — so the SSE event is `{ type, unreadCount }`. Reconnect/backoff on connection loss.
- **`GET /api/v1/notifications/stream`** on `NotificationsController`: returns an `SseEmitter`
  (long timeout), registers it for `principal.id()`, sends an initial `{ unreadCount }` snapshot so
  the badge is correct on connect, and relies on the registry for subsequent pushes.

To keep the listener from re-querying per event under a burst, the pushed `type` can be carried in
the NOTIFY payload as `recipientUserId|type` (still tiny, well under the 8000-byte limit); the
listener parses it and only queries `unreadCount`.

### 5. SSE authentication

`EventSource` cannot send `Authorization`. Extend token extraction so the **stream route only** also
reads `?access_token=` and validates it with `JwtTokenService` (same verification, same
`AuthenticatedUser`). All other routes remain header-only. Documented trade-off: query tokens may
appear in access logs; mitigated by the short access-token TTL and the single-route scope.

---

## Sub-issues

- **SI-14.1 — Slice relocation:** move entity/repo/adapter to shared persistence; drop the API-only
  scan entries; rewrite `findFeed` as a native query + `@ConstructorResult` mapping. Green build
  proves both bootstraps still map it (worker included).
- **SI-14.2 — Worker events:** `NotificationType.VIDEO_READY/VIDEO_FAILED` + factories;
  `ProcessVideoUseCase` `finishReady` (transactional) and `markFailed` emit; wire the two new deps
  in `WorkerBeans`.
- **SI-14.3 — V14 trigger:** `pg_notify` function + `AFTER INSERT` trigger.
- **SI-14.4 — SSE registry + listener:** `SseEmitterRegistry`, `PostgresNotificationListener`
  (dedicated connection, LISTEN loop, backoff), lifecycle (`@PostConstruct`/`DisposableBean`).
- **SI-14.5 — Stream endpoint + auth:** `GET /notifications/stream`, initial snapshot, query-param
  token for this route only.
- **SI-14.6 — Tests:** worker unit (READY/FAILED create the right notification; redelivery no dup;
  FAILED shares the `markFailed` tx); registry unit (push/complete/remove); listener→registry wiring
  test; Testcontainers integration that opens the stream (MockMvc async), triggers a notification,
  and asserts an SSE event with the updated `unreadCount`; extend the Phase 13 E2E to assert a
  `VIDEO_READY` row after processing. compose smoke (two API replicas optional).
- **SI-14.7 — Docs + DoD:** `system-design.md` (§4 worker events + §6 real-time delivered; the
  `LISTEN/NOTIFY` topology), `GUIA-DE-USO.md` (stream route + the two new types), Postman
  (stream request note), `progress.md`; `./gradlew spotlessApply build` green.

## Dependency Map

```
SI-14.1 ──┬─ SI-14.2 ─────────────┐
          └─ SI-14.3 ─ SI-14.4 ─ SI-14.5 ─┴─ SI-14.6 ─ SI-14.7
```

## Deliverables

1. `docs/phases/phase-14-notifications-realtime/` — context, plan, validation, progress
2. Notifications slice em persistência compartilhada (worker passa a escrever no feed)
3. `VIDEO_READY` / `VIDEO_FAILED` emitidos pelo worker, atômicos com a escrita de status
4. Trigger V14 `pg_notify` — todo insert de notificação sinaliza no commit, de qualquer processo
5. Entrega em tempo real: `GET /notifications/stream` (SSE) + listener `LISTEN/NOTIFY` por instância
6. Autenticação do stream por `?access_token=` (só nessa rota)
7. Unit + Testcontainers (SSE recebe evento após gatilho; worker emite READY) + smoke no compose
