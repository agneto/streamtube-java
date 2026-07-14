# Phase 13 — In-App Notifications (plan)

## Objective

An in-app notification feed driven by the existing social surface: subscribing, commenting,
replying, and publishing a public video each drop a row into the affected user's feed; the user
lists it, sees an unread count, and marks items read. Entirely API-side, one new table, no worker
or scheduler changes.

---

## Technical Specifications

### Data model — migration V13

```sql
CREATE TABLE notifications (
    id                uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type              varchar(32) NOT NULL,
    actor_channel_id  uuid REFERENCES channels (id) ON DELETE CASCADE,
    video_id          uuid REFERENCES videos (id) ON DELETE CASCADE,
    comment_id        uuid REFERENCES comments (id) ON DELETE CASCADE,
    read_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);
-- Feed listing (recipient, newest first):
CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_user_id, created_at DESC);
-- Unread count / unread filter (partial: only the rows that matter):
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_user_id)
    WHERE read_at IS NULL;
```

All four FKs cascade: deleting a user/channel/video/comment retires the notifications that point at
it (Phase 11's `DELETE /videos/{id}` already cascades). No orphan sweeper.

### Domain

```java
enum NotificationType { NEW_SUBSCRIBER, VIDEO_COMMENT, COMMENT_REPLY, NEW_VIDEO }

record Notification(UUID id, UUID recipientUserId, NotificationType type,
                    UUID actorChannelId, UUID videoId, UUID commentId,
                    Instant readAt, Instant createdAt) { /* factory helpers per type */ }

interface NotificationRepository {                       // domain/notification
  void create(Notification n);                           // single-recipient insert
  int  fanOutNewVideo(UUID channelId, UUID videoId, Instant at);  // INSERT..SELECT over subscriptions
  long unreadCount(UUID recipientUserId);
  boolean markRead(UUID id, UUID recipientUserId);       // scoped; false if not theirs / already read
  int  markAllRead(UUID recipientUserId);
  PageResult<NotificationRow> findPage(UUID recipientUserId, int page, int size);  // projection
}
```

`NotificationRow` is the join projection (type, read flag, timestamps + actor channel and video
fields). The application maps it to a `NotificationView` and presigns actor avatar / video
thumbnail keys, as `VideoInfoView`/`CommentView` do.

### Trigger hooks (existing use cases, in-transaction)

- **`SubscribeUseCase`** — after `subscriptions.subscribe(userId, channel.id())` returns `true`
  (real new row), create `NEW_SUBSCRIBER` (recipient = `channel.userId()`, actor = the subscriber's
  channel via `findByUserId(userId)`).
- **`CreateCommentUseCase`** — after the comment saves:
  - top-level (`parentId == null`): `VIDEO_COMMENT` to the video owner, unless the commenter's
    channel is the video's channel (own video).
  - reply: `COMMENT_REPLY` to `parent.userId()`, unless that equals the commenter (own comment).
  - actor = commenter's channel; refs = video + comment.
- **`PublishVideoUseCase`** — capture `wasPublished = video.isPublished()` before `publish()`; if it
  was not published before, is now, **and** `visibility == PUBLIC`, call
  `fanOutNewVideo(channel.id(), saved.id(), now)`. Republish (no-op) and non-public visibility emit
  nothing.

### Read use cases + web

`ListNotificationsUseCase` (paged, presigns), `GetUnreadCountUseCase`, `MarkNotificationReadUseCase`,
`MarkAllNotificationsReadUseCase` — all take the caller's `userId` and are recipient-scoped.

`NotificationsController` (`/api/v1/notifications`):
`GET ""` → paged `NotificationView`; `GET "/unread-count"` → `{ count }`;
`POST "/{id}/read"` → 204; `POST "/read-all"` → 204.

### Persistence slice

`infrastructure.notification`: `NotificationEntity`, `NotificationJpaRepository`
(`create` via save; `unreadCount`; `@Modifying markRead`/`markAllRead`; **native** `INSERT..SELECT`
fan-out; a JPQL projection `LEFT JOIN`ing `ChannelEntity` on `actor_channel_id` and `VideoEntity`
on `video_id`), `NotificationRepositoryAdapter`. Register the package in the API's `@EntityScan`
and `@EnableJpaRepositories` (next to `infrastructure.social`). The worker is untouched.

---

## Sub-issues

- **SI-13.1 — Domain:** `NotificationType`, `Notification` (+ per-type factories),
  `NotificationRepository` port. No changes to existing entities.
- **SI-13.2 — Flyway V13:** `notifications` table + the two indexes.
- **SI-13.3 — Persistence slice:** entity, JPA repo (native fan-out + projection query, modifying
  mark-read), adapter; register `infrastructure.notification` in the API scan config.
- **SI-13.4 — Trigger hooks:** `SubscribeUseCase`, `CreateCommentUseCase`, `PublishVideoUseCase`
  with the self-notification and first-publish/PUBLIC guards.
- **SI-13.5 — Read + web:** four read/mark use cases, `NotificationView` (+ presigning),
  `NotificationsController`.
- **SI-13.6 — Tests:** unit (each trigger incl. every guard: re-subscribe no-op, own-video/own-comment
  suppressed, republish/non-public no fan-out; fan-out inserts one row per subscriber and excludes
  the publisher; mark-read ownership; unread count) + Testcontainers E2E (A subscribes to B →
  B publishes public video → A gets `NEW_VIDEO`; A comments → B gets `VIDEO_COMMENT`; B replies →
  A gets `COMMENT_REPLY`; unread-count then read-all; deleting the video cascades its notifications
  away) + compose smoke.
- **SI-13.7 — Docs + DoD:** `system-design.md` (notifications now shipped; note the deferred
  worker-sourced `VIDEO_READY` + real-time push as evolution), `GUIA-DE-USO.md` (Fase 13 table +
  §5 routes), Postman collection (notifications folder), `progress.md`; `./gradlew build` green.

## Dependency Map

```
SI-13.1 ── SI-13.2 ── SI-13.3 ──┬── SI-13.4 ──┐
                                └── SI-13.5 ──┴── SI-13.6 ── SI-13.7
```

## Deliverables

1. `docs/phases/phase-13-notifications/` — context, plan, validation, progress
2. Tabela `notifications` (V13) com cascatas de FK cuidando da limpeza
3. Quatro gatilhos sociais (novo inscrito, comentário, resposta, novo vídeo) gerando notificações
4. Fan-out de "novo vídeo" como um único `INSERT..SELECT` sobre `subscriptions`
5. Feed autenticado: listar, contador de não lidas, marcar lida / marcar todas
6. Unit + Testcontainers E2E (incl. cascata na deleção) + smoke no stack compose
