# Phase 07 — Home, Search & Production (context)

## Goal

Implement the backend of the reference project's Phase 07 ("Página Inicial, Busca e
Finalização", `project-plan.md`): the home-page video grid (with category filter), the search bar
(by video title and channel name) and the production/deploy story — closing the reference
roadmap. Header/navbar, infinite scroll and responsive layout are frontend-only; pagination
already exists (Phase 04 convention).

## Endpoints to add or change

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/videos?page&size&categoryId` | public | Home grid: published + PUBLIC, newest first, optional category filter |
| GET | `/api/v1/search?q=&page&size` | public | Videos matching the query by **title or channel name**, newest first |

Both return the new **video card** item — the grid card needs the channel identity, which the
existing `VideoSummaryResponse` (channel-page listing) deliberately omits:

```json
{ "id", "slug", "title", "thumbnailUrl", "durationSeconds", "views",
  "publishedAt", "categoryId", "channel": { "id", "name", "nickname" } }
```

No behavior change to any existing endpoint.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Home listing | Global `published + PUBLIC`, `published_at DESC` ("tempo de publicação" is what the card shows). `categoryId` optional filter (uuid — the categories endpoint already hands ids to the frontend). Standard page envelope; no ranking/recommendation (out of the reference scope). |
| Search semantics | One endpoint, `q` matched case-insensitively as a **contains** against the video title **or** the owning channel's name — exactly the reference "pesquisa por título e canal". Only published + PUBLIC. Ordered `published_at DESC`; relevance ranking is a recorded non-goal (trade-off: simple, predictable, index-backed). Blank/short `q` (< 2 chars) → 400. |
| Search indexing | Migration V9 enables `pg_trgm` and adds GIN trigram indexes on `videos.title` and `channels.name`, so the `ILIKE '%q%'` predicates stay index-backed as data grows. The home listing gets a partial index `(published_at DESC) WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL`. |
| Card assembly | Channel identities resolved with one batch query per page (`findByIds`, same pattern as comment authors) — never N+1. |
| Production/deploy | `compose.prod.yaml` override: no dev port exposure (Postgres/RabbitMQ/Mailpit internal only), `restart: unless-stopped`, real SMTP vars replacing Mailpit, required secrets fail-fast (no dev defaults). `docs/deploy.md` documents the checklist (secrets, CORS_ALLOWED_ORIGINS, storage/public URL, sizing) — actual cloud hosting is the user's call and out of scope. CORS is already env-driven (`CORS_ALLOWED_ORIGINS`, Phase 02). |
| Security | `GET /api/v1/search` joins the public allowlist (`GET /api/v1/videos/**` already covers the home listing). |

## Lessons carried over

- Pagination convention (page/size clamps, `PageResult` envelope) — reused as-is.
- Partial indexes over exactly the rows public queries read (Phases 04–05).
- Native SQL for queries the worker's persistence unit must not know about — search joins
  `channels`, which the worker maps, so JPQL is fine here; native only if it touches social
  tables.
- Batch lookups over per-item queries (Phase 06 comment authors).
- E2E: unique fake IP per test user (auth rate limit).

## Out of scope

- Relevance ranking, typo tolerance, full-text search (tsvector) — trigram contains is the MVP.
- Recommendations/trending on the home page.
- Frontend items: header/navbar, infinite scroll, responsive layout.
- Actual cloud deployment (host choice, DNS, TLS termination) — documented, not executed.
- Watch history / continue watching (not in the reference plan).
