# Phase 07 — Home, Search & Production (plan)

## Objective

Deliver the home-page grid (global listing with category filter), search by title/channel and the
production deployment story — the backend of the reference plan's final phase.

---

## Technical Specifications

### Data Model — migration V9

No new tables, no new columns. Indexes only:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_videos_title_trgm ON videos USING gin (title gin_trgm_ops);
CREATE INDEX idx_channels_name_trgm ON channels USING gin (name gin_trgm_ops);
CREATE INDEX idx_videos_listed_published
    ON videos (published_at DESC)
    WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL;
```

### API Contracts (all under `/api/v1`)

- `GET /videos?page&size&categoryId` (public) → page envelope of video cards: published + PUBLIC,
  `published_at DESC`; `categoryId` optional (unknown id → empty page, not an error).
- `GET /search?q=texto&page&size` (public) → same envelope/card; `q` required, trimmed, minimum
  2 chars (400 `INVALID_SEARCH_QUERY` otherwise); matches `ILIKE '%q%'` on video title **or**
  channel name; published + PUBLIC only; `published_at DESC`.
- Video card item: `{id, slug, title, thumbnailUrl, durationSeconds, views, publishedAt,
  categoryId, channel {id, name, nickname}}` — thumbnail presigned like every listing.

### Repository port additions

```java
// VideoRepository
PageResult<Video> findListedPage(UUID categoryId /* nullable */, int page, int size);
PageResult<Video> searchListed(String query, int page, int size); // title OR channel name
```

`searchListed` joins `channels` in the adapter (JPQL is fine: the worker maps both entities; only
social tables require native SQL).

### Authorization Matrix

| Action | Anonymous | Authenticated |
|---|---|---|
| Home grid / category filter | ✔ | ✔ |
| Search | ✔ | ✔ |

Drafts and UNLISTED videos never appear in either (the Phase 04 listing rule, re-applied in the
queries themselves).

### Production/deploy deliverables

- `compose.prod.yaml` override: internal-only Postgres/RabbitMQ (no host ports), no Mailpit
  (real SMTP via env), `restart: unless-stopped`, secrets **required** (compose fails fast when
  `JWT_SECRET`/`DB_PASSWORD`/etc. are unset — no dev defaults in prod).
- `docs/deploy.md`: environment checklist (secrets, `CORS_ALLOWED_ORIGINS`, storage public URL,
  SMTP), how to run (`docker compose -f compose.yaml -f compose.prod.yaml up -d`), migration
  story (API owns Flyway), sizing/scaling notes (API stateless → replicas; worker scales by
  queue depth).

---

## Sub-issues

- **SI-07.1 — Domain:** `VideoRepository.findListedPage`/`searchListed`; new
  `InvalidSearchQueryException` (`VALIDATION`). No entity changes (first phase without a
  constructor ripple).
- **SI-07.2 — Flyway V9:** `pg_trgm` + trigram GIN indexes + partial home index.
- **SI-07.3 — Persistence:** listing query (derived, optional category) and search query (JPQL
  join with `channels`, `lower(...) like lower(...)` on title/name), both Pageable-limited in the
  adapter.
- **SI-07.4 — Use cases:** `ListHomeVideosUseCase` (optional category), `SearchVideosUseCase`
  (trim + min-length rule); `VideoCardView` result with channel identity resolved via one batch
  `findByIds` per page.
- **SI-07.5 — Web:** `GET /videos` on `VideosController` (must not capture `/{slug}` routes —
  method on the collection path), `SearchController` (`GET /api/v1/search`); SecurityConfig:
  `GET /api/v1/search` permitAll; Postman folder "Home & Busca".
- **SI-07.6 — Prod:** `compose.prod.yaml` + `docs/deploy.md`.
- **SI-07.7 — Tests + docs + DoD:** unit (min-length 400, category filter pass-through, card
  assembly), E2E (home lists only published PUBLIC newest first; category filter; search by
  title and by channel name; UNLISTED/draft excluded from both; q curto 400), docs
  (system-design, GUIA-DE-USO, progress.md), `./gradlew build` verde, smoke compose.

## Dependency Map

```
SI-07.1 ── SI-07.2 ── SI-07.3 ── SI-07.4 ── SI-07.5 ──┬── SI-07.7
                                            SI-07.6 ──┘
```

## Deliverables

1. `docs/phases/phase-07-home/` — context, plan, validation, progress
2. Migration V9: pg_trgm + índices de busca e listagem global
3. `GET /api/v1/videos` — grid da home com filtro por categoria
4. `GET /api/v1/search` — busca por título e nome de canal
5. `compose.prod.yaml` + `docs/deploy.md` — história de produção
6. Unit + Testcontainers E2E; `./gradlew build` verde
