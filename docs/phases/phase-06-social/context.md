# Phase 06 — Social Interactions (context)

## Goal

Implement the backend of the reference project's Phase 06 ("Interações Sociais",
`project-plan.md`): like/dislike on videos and on comments, comments with single-level replies,
channel subscriptions with subscriber count, and the subscribed-channels area (list + video
feed) — in Clean Architecture, under `/api/v1`.

> Frontend-only items of the reference phase ("interface completa de comentários, likes e
> inscrições") become the REST endpoints that would power them.

## Endpoints to add or change

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| PUT | `/api/v1/videos/{id}/reaction` | user | Set my reaction (`LIKE` \| `DISLIKE`); switching adjusts both counters |
| DELETE | `/api/v1/videos/{id}/reaction` | user | Remove my reaction (idempotent) |
| POST | `/api/v1/videos/{id}/comments` | user | Comment; `parentId` optional (single-level reply) |
| GET | `/api/v1/videos/{slug}/comments` | public | Top-level comments, paginated, newest first |
| GET | `/api/v1/comments/{id}/replies` | public | Replies of a comment, paginated, oldest first |
| DELETE | `/api/v1/comments/{id}` | author | Delete own comment (cascades its replies) |
| PUT | `/api/v1/comments/{id}/reaction` | user | Like/dislike a comment |
| DELETE | `/api/v1/comments/{id}/reaction` | user | Remove comment reaction (idempotent) |
| PUT | `/api/v1/channels/{nickname}/subscription` | user | Subscribe (idempotent; self-subscribe → 400) |
| DELETE | `/api/v1/channels/{nickname}/subscription` | user | Unsubscribe (idempotent) |
| GET | `/api/v1/subscriptions` | user | Channels I subscribe to, paginated |
| GET | `/api/v1/subscriptions/videos` | user | Feed: latest published + PUBLIC videos of subscribed channels |

Behavior changes to existing endpoints: video info gains `likes`, `dislikes`, `commentsCount` and
`myReaction` (null when anonymous/no reaction); public channel page gains `subscribersCount` and
`subscribed` (false when anonymous).

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Counters | Denormalized counters (`videos.likes_count/dislikes_count/comments_count`, `comments.likes_count/dislikes_count/replies_count`, `channels.subscribers_count`) changed only by atomic `± 1` SQL behind the ports, in the **same transaction** as the source-row change. Source of truth stays the normalized tables (`video_reactions`, `comment_reactions`, `comments`, `subscriptions`) with unique constraints, so counters are always recomputable and double-counting is impossible. |
| Reaction semantics | One reaction per user per target (unique `(user_id, video_id)` / `(user_id, comment_id)`). `PUT` sets or switches (switch adjusts both counters in one tx), `DELETE` removes; both idempotent. |
| Comment nesting | Single level, YouTube-style: `parentId` must reference a **top-level** comment of the same video; replying to a reply → 400. `commentsCount` counts top-level + replies; deleting a top-level comment removes its replies and decrements by `1 + replies` in the same tx. |
| Comment content | 1–2000 chars, non-blank. No editing (not in the reference list). Author identity exposed via the author's channel (id, name, nickname). |
| Interaction visibility | Interactions follow the Phase 04 read rule: only **published** videos accept/expose reactions and comments (draft → 404 for non-owners, 409 `VIDEO_NOT_PUBLISHED` for the owner). Comment listing on UNLISTED videos works by slug like the video itself. |
| Subscriptions | `user → channel`, unique pair; self-subscribe → 400. Subscribe/unsubscribe idempotent: the counter only moves when a row is actually inserted/deleted. Public channel page shows `subscribersCount`; `subscribed` requires auth. |
| Feed | `GET /subscriptions/videos` returns published + PUBLIC videos of subscribed channels, `published_at DESC`, standard page envelope — the backend of "área de canais seguidos com acesso rápido aos vídeos". |
| Security | Single change: `GET /api/v1/comments/**` becomes permitAll (replies listing). Everything else is already covered (`GET /videos/**` public, writes fall into the authenticated default). |

## Lessons carried over

- Counters: atomic SQL only, `updatable = false` on the JPA entities (Phase 05 pattern) so a stale
  `save()` can never erase concurrent updates.
- Plain UUID FK columns (no JPA associations) — worker persistence unit stays minimal; new tables
  are API-only and must NOT enter the worker's entity scan.
- Pagination convention from Phase 04 (`page`/`size` clamped, `PageResult` envelope).
- Every new domain exception carries a `DomainErrorType`; the handler switch is exhaustive.
- E2E: unique fake IP per test user (auth rate limit).

## Out of scope

- Editing comments; moderation by the video owner (deleting others' comments).
- Notifications (new video/reply) — no phase in the reference plan asks for them.
- Dedup/abuse protection beyond the unique constraints.
- Home/search/ranking (Phase 07); watch progress.
