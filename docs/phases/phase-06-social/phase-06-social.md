# Phase 06 — Social Interactions (plan)

## Objective

Deliver likes/dislikes on videos and comments, comments with single-level replies, and channel
subscriptions (with subscriber count and a subscribed-channels feed) on top of the Phase 04/05
publication model — the backend of the reference project plan's Phase 06.

---

## Technical Specifications

### Data Model — migration V8

**New tables:**

| Table | Columns | Constraints |
|-------|---------|-------------|
| video_reactions | user_id, video_id, type varchar(7), created_at | PK (user_id, video_id); FKs cascade on delete; index (video_id) |
| comments | id uuid, video_id, user_id, parent_id nullable, content varchar(2000), likes_count, dislikes_count, replies_count, created_at | FK parent_id → comments on delete cascade; index (video_id, parent_id, created_at DESC) |
| comment_reactions | user_id, comment_id, type varchar(7), created_at | PK (user_id, comment_id); FK cascade; index (comment_id) |
| subscriptions | user_id, channel_id, created_at | PK (user_id, channel_id); FKs cascade; index (channel_id), index (user_id, created_at DESC) |

**New counter columns** (all `bigint not null default 0`, atomic `± 1` only):
`videos.likes_count`, `videos.dislikes_count`, `videos.comments_count`,
`channels.subscribers_count` (comment counters live on the table above).

No backfill: all counters legitimately start at 0.

### Domain rules

- `ReactionType` enum: `LIKE` | `DISLIKE`.
- `Comment` entity: content 1–2000 chars non-blank (domain invariant); replies are single-level —
  a reply's `parentId` must be a top-level comment of the same video (use-case rule, 400
  `INVALID_PARENT_COMMENT` otherwise); only the author may delete.
- Interactions (react/comment/list) require a **published** video: 404 for non-owners on drafts
  (no existence leak, reuses `VideoViewAccess`), 409 `VIDEO_NOT_PUBLISHED` for the owner.
- Subscription: `user → channel`, self-subscribe → 400 `SELF_SUBSCRIPTION`; idempotent both ways.
- Counters are read-only on the domain entities (Phase 05 pattern): every mutation is an atomic
  SQL statement behind the repository ports, in the same transaction as the source-row change.

### API Contracts (all under `/api/v1`)

- `PUT /videos/{id}/reaction` `{type}` → 204. Sets or switches; switch adjusts both counters in
  one tx. `DELETE /videos/{id}/reaction` → 204, idempotent.
- `POST /videos/{id}/comments` `{content, parentId?}` → 201 comment view
  `{id, videoId, parentId, content, author{channelId, name, nickname}, likes, dislikes,
  repliesCount, createdAt}`.
- `GET /videos/{slug}/comments?page&size` (public) → page envelope of top-level comment views,
  newest first. `GET /comments/{id}/replies?page&size` (public) → replies, oldest first.
- `DELETE /comments/{id}` → 204 (author only, 403 otherwise); cascades replies and decrements the
  video's `commentsCount` by `1 + replies`.
- `PUT /comments/{id}/reaction` `{type}` / `DELETE` → 204 (same semantics as video reactions).
- `PUT /channels/{nickname}/subscription` → 204 (400 self, idempotent).
  `DELETE /channels/{nickname}/subscription` → 204 (idempotent).
- `GET /subscriptions?page&size` → page envelope of `{channelId, name, nickname, description,
  subscribersCount, subscribedAt}`. `GET /subscriptions/videos?page&size` → page envelope of
  `VideoSummaryResponse` (published + PUBLIC of subscribed channels, `published_at DESC`).
- `VideoInfoResponse` gains `likes`, `dislikes`, `commentsCount`, `myReaction` (nullable).
  `ChannelInfoResponse` (public page) gains `subscribersCount` and `subscribed`.

### Repository port additions

```java
// VideoReactionRepository (new)
Optional<ReactionType> find(UUID userId, UUID videoId);
void set(UUID userId, UUID videoId, ReactionType type);   // upsert + counter deltas, one tx
void remove(UUID userId, UUID videoId);                   // delete + counter decrement

// CommentRepository (new)
Comment save(Comment comment);                            // insert + comments_count/replies_count ++
Optional<Comment> findById(UUID id);
PageResult<Comment> findTopLevelByVideoId(UUID videoId, int page, int size);
PageResult<Comment> findRepliesByParentId(UUID parentId, int page, int size);
void delete(Comment comment);                             // cascade + counter decrements

// CommentReactionRepository (new) — same shape as VideoReactionRepository

// SubscriptionRepository (new)
boolean subscribe(UUID userId, UUID channelId);           // ON CONFLICT DO NOTHING; counter ++ only if inserted
boolean unsubscribe(UUID userId, UUID channelId);         // counter -- only if deleted
boolean exists(UUID userId, UUID channelId);
PageResult<Subscription> findPageByUserId(UUID userId, int page, int size);

// VideoRepository (existing)
PageResult<Video> findSubscriptionFeed(UUID userId, int page, int size); // published + PUBLIC
```

### Authorization Matrix

| Action | Anonymous | Authenticated | Author/Owner |
|---|---|---|---|
| Read comments/replies of a published video | ✔ | ✔ | ✔ |
| Read comments of a draft | ✖ (404) | ✖ (404) | ✔ (owner) |
| React / comment on a published video | ✖ (401) | ✔ | ✔ |
| React / comment on any draft | ✖ | ✖ (404 / 409 owner) | ✖ (409) |
| Delete a comment | ✖ | ✖ (403) | ✔ (author) |
| Subscribe / feed / my subscriptions | ✖ (401) | ✔ | — (self-subscribe 400) |
| See subscribersCount on channel page | ✔ | ✔ | ✔ |

---

## Sub-issues

- **SI-06.1 — Domain:** `ReactionType`, `Comment`, `Subscription`; new ports
  (`VideoReactionRepository`, `CommentRepository`, `CommentReactionRepository`,
  `SubscriptionRepository`) + `VideoRepository.findSubscriptionFeed`; counter accessors on
  `Video` (`likesCount`/`dislikesCount`/`commentsCount`) and `Channel` (`subscribersCount`) —
  constructor ripple on both (mapper + every fixture).
- **SI-06.2 — Flyway V8:** 4 tables + counter columns + indexes.
- **SI-06.3 — Persistence:** entities (API-only: keep them OUT of the worker's persistence scan),
  mappers, adapters; atomic counter statements; reaction upsert (`ON CONFLICT`) and
  insert/delete-detection for subscription idempotency; counters `updatable = false`.
- **SI-06.4 — Use cases (reactions):** `SetVideoReactionUseCase`, `RemoveVideoReactionUseCase`,
  `SetCommentReactionUseCase`, `RemoveCommentReactionUseCase`; published-only rule; `myReaction`
  wired into `GetVideoInfoUseCase`.
- **SI-06.5 — Use cases (comments):** `CreateCommentUseCase` (top-level + reply, parent
  validation), `ListCommentsUseCase`, `ListRepliesUseCase`, `DeleteCommentUseCase` (author only,
  counter math with replies).
- **SI-06.6 — Use cases (subscriptions):** `SubscribeUseCase`/`UnsubscribeUseCase` (idempotent,
  self-subscribe 400), `ListMySubscriptionsUseCase`, `GetSubscriptionFeedUseCase`;
  `subscribersCount`/`subscribed` wired into `GetPublicChannelUseCase`.
- **SI-06.7 — Web:** `CommentsController`, `SubscriptionsController`, new routes on
  `VideosController`/`ChannelsController`; DTOs; SecurityConfig: `GET /api/v1/comments/**`
  permitAll (only change); Postman folder "Social" + tests.
- **SI-06.8 — Tests + docs + DoD:** unit (reaction switch deltas, reply-to-reply 400, delete
  counter math, self-subscribe, idempotency) + E2E (like→switch→remove com contadores; fluxo de
  comentário/resposta/delete; subscribe→count→feed→unsubscribe; matriz draft/anon), docs
  (system-design §componentes/ER, GUIA-DE-USO, progress.md), `./gradlew build` verde, smoke da
  stack compose.

## Dependency Map

```
SI-06.1 ── SI-06.2 ── SI-06.3 ──┬── SI-06.4 ──┬── SI-06.7 ── SI-06.8
                                ├── SI-06.5 ──┤
                                └── SI-06.6 ──┘
```

## Deliverables

1. `docs/phases/phase-06-social/` — context, plan, validation, progress
2. Migration V8: reações, comentários, inscrições + contadores atômicos
3. Like/dislike em vídeos e comentários (um por usuário, switch e remoção idempotentes)
4. Comentários com respostas de um nível e deleção pelo autor
5. Inscrições com contagem pública, listagem e feed de vídeos dos canais seguidos
6. Unit + Testcontainers E2E; `./gradlew build` verde
