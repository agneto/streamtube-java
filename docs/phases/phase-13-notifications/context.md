# Phase 13 — In-App Notifications (context)

## Goal

Give users an in-app notification feed: when someone interacts with them or with content they
follow, a row lands in their feed and their unread badge ticks up. This is a genuine post-roadmap
evolution — `phase-06-social/context.md` explicitly recorded "Notifications (new video/reply) — no
phase in the reference plan asks for them" — so it is designed fresh, like the other post-1.0
phases, from the social surface already shipped in Phase 06.

Four events produce notifications, all originating on the **API** side:

| Type | Recipient | Actor | Fires when |
|------|-----------|-------|-----------|
| `NEW_SUBSCRIBER` | channel owner | the subscriber's channel | someone subscribes (only on a real new row) |
| `VIDEO_COMMENT` | video owner | the commenter's channel | a top-level comment is posted on their video |
| `COMMENT_REPLY` | parent comment's author | the replier's channel | someone replies to their comment |
| `NEW_VIDEO` | every subscriber of the channel | the channel | the owner publishes a **public** video (first publish) |

The first three are single-recipient inserts; `NEW_VIDEO` is a **fan-out** to all subscribers.

## Endpoints to add (all `/api/v1`, authenticated, self-scoped)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/notifications` | my feed, newest first, paged |
| GET | `/notifications/unread-count` | badge count (`{ "count": N }`) |
| POST | `/notifications/{id}/read` | mark one as read (204; idempotent) |
| POST | `/notifications/read-all` | mark all mine as read (204) |

Every route is scoped to the caller: a user can only ever list or mark their own notifications.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Data model | One `notifications` table, referential (not snapshotted): `recipient_user_id`, `type`, nullable `actor_channel_id` / `video_id` / `comment_id`, `read_at` (null = unread), `created_at`. Display (actor name/avatar, video title/thumbnail) is resolved at read time by joining channels/videos — consistent with the codebase's read-view style (`CommentView.from`, `VideoCards`) rather than denormalizing snapshots. |
| Recipient vs. actor | The **recipient** is a `user_id`; the **actor** is a `channel` (so the feed shows a channel name + avatar). The commenter's/subscriber's actor channel is resolved via `channelRepository.findByUserId`; the video owner's recipient user is resolved via the video's channel. Never conflate user and channel ids. |
| Transaction boundary | A notification is a row in the **same** database, so it is written **inside the triggering transaction** — not through `AfterCommitExecutor` (that exists for external systems: SMTP, the queue). If the comment/subscribe rolls back, its notification rolls back too. |
| Idempotency | Subscribe notifies **only when a row is actually inserted** (`subscribe()` already returns that boolean) — re-subscribing must not spam, mirroring the subscribers-count rule. Republish is a domain no-op, so `NEW_VIDEO` fan-out fires only on the first publish. |
| Self-notifications | Suppressed: commenting on your own video, replying to your own comment. Self-subscribe is already a 400, and a publisher is never subscribed to their own channel, so `NEW_VIDEO` fan-out excludes them naturally. |
| Fan-out | `NEW_VIDEO` is a single native `INSERT INTO notifications (...) SELECT ... FROM subscriptions WHERE channel_id = ?` — one statement, not row-by-row. It lives on the API side (it joins the social `subscriptions` table), exactly like the native `findSubscriptionFeed` from Phase 06. |
| Persistence boundary | Notifications get their **own API-only slice** (`infrastructure.notification`), registered in the API's `@EntityScan`/`@EnableJpaRepositories` alongside `infrastructure.social`. The worker's persistence unit never maps it. |
| Cleanup | None needed. FK cascades (`recipient_user_id`→users, `actor_channel_id`→channels, `video_id`→videos, `comment_id`→comments, all `ON DELETE CASCADE`) retire notifications when their subject disappears — Phase 11's video deletion already cascades them away. No sweeper, no outbox. |
| Delivery | **In-app only**: the client fetches the feed and polls `unread-count`. No real-time push and no email in this phase. |

## Lessons carried over

- Atomic, set-based SQL over row-by-row loops (the counter/`findSubscriptionFeed` pattern) — fan-out
  is one `INSERT ... SELECT`.
- Read views assembled from joins at query time, storage keys presigned in the use case
  (`VideoInfoView`/`CommentView` precedent) — the notification feed presigns actor avatar + video
  thumbnail.
- API-only social surfaces stay out of the worker's persistence unit (the `infrastructure.social`
  boundary) — notifications follow the same rule.
- FK `ON DELETE CASCADE` as the cleanup mechanism (Phase 11 ethos: the row's death takes its
  dependents with it) — no bespoke orphan handling.

## Out of scope

- **Real-time delivery** (WebSocket/SSE push) — the feed is poll-based; live push is a clean later
  layer over the same table.
- **Email / mobile push** of notifications, and **per-type preferences / muting**.
- **`VIDEO_READY` / `VIDEO_FAILED`** (owner told their upload finished processing): these originate
  in the **worker**, which would force the notifications slice into shared persistence + worker
  wiring. Deferred deliberately to keep this phase entirely API-side; noted as the obvious next step.
- **Reaction (like/dislike) notifications** — too noisy to emit per-event; would need aggregation.
- **Aggregation / grouping** ("3 people liked your video").
