# Phase 05 — Watch Page (context)

## Goal

Implement the backend of the reference project's Phase 05 ("Página de Visualização do Vídeo",
`project-plan.md` of the Next/Nest reference): view counting and same-category video suggestions
for the watch page sidebar — in Clean Architecture, under `/api/v1`.

> The reference phase is mostly frontend (player, layout, description expand/collapse). Its
> backend-relevant items are **view counts** and **suggestions**; everything else it lists —
> anonymous access, download button, UNLISTED reachable only by link, description — was already
> delivered by Phases 03–04. As before, frontend-only items become the REST endpoints that would
> power them.

## Endpoints to add or change

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/videos/{slug}/related` | public | Watch-page sidebar: published PUBLIC videos of the same category, excluding the video itself (`?limit`, default 10, max 20) |

Behavior changes to existing endpoints:

- `GET /api/v1/videos/{slug}/stream` — issuing a stream URL for a **published** video counts one
  view (atomic increment).
- `GET /api/v1/videos/{slug}` and every listing item (`related`, panel, public channel page) —
  response gains a `views` field.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| View storage | Counter column `videos.views_count bigint not null default 0`. No event table: the reference plan only asks for a count, and an events table grows unbounded for value we don't use yet (recorded as the alternative if analytics/dedup ever land). |
| What counts as a view | One successful `GET /{slug}/stream` (the closest server-side signal of playback). Download does **not** count. Only **published** videos accumulate views — the owner previewing a draft is not an audience. |
| Dedup | None: reloads/replays count again. Accepted trade-off (YouTube-grade dedup needs sessions/windows and is out of scope); a per-viewer window can be added later without schema change. |
| Increment mechanics | Atomic `UPDATE videos SET views_count = views_count + 1 WHERE id = ?` behind a dedicated repository port method. Never read-modify-write through the entity: concurrent viewers would lose updates. The domain entity only exposes the count read-only. |
| Suggestions ("related") | Same `category_id`, published + PUBLIC only (visibility matrix of Phase 04 applies), excluding the video itself, ordered `published_at DESC`, plain `limit` (no page envelope — it's a sidebar). Base video without category → fallback to the latest published PUBLIC videos platform-wide (still excluding itself). Access to the base video follows the read rule: draft → 404 for non-owners. |
| Ordering of existing listings | Unchanged (chronological). `views` is exposed, not used for ranking — ranking/search is Phase 07. |

## Lessons carried over

- Counter updates must be atomic in SQL (same class of race as nickname/email uniqueness: never
  check-then-write through the entity when concurrency is expected).
- New partial index only over the rows the public query reads (same pattern as
  `idx_videos_channel_published`).
- `Video` constructor grows by one field — the ripple hits `PersistenceMapper`, `VideoEntity` and
  every test fixture that builds a `Video`; plan for it instead of discovering it.
- Read endpoints stay under the existing `GET /api/v1/videos/**` allowlist — no security config
  change needed.

## Out of scope

- Likes/dislikes, comments, subscriptions (Phase 06).
- Home grid, search, category filter on home, ranking by views (Phase 07).
- View dedup / unique-viewer analytics; view counts per time window.
- Watch progress / resume position.
