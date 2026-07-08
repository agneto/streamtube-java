# Phase 05 — Watch Page (plan)

## Objective

Deliver the backend of the watch page on top of the Phase 04 publication model: view counting on
playback and a same-category suggestions listing, with counts exposed across every video read —
matching the reference project plan's Phase 05, backend side.

---

## Technical Specifications

### Data Model — migration V7

**`videos` — new column:**

| Column | Type | Notes |
|--------|------|-------|
| views_count | bigint not null default 0 | monotonic counter, atomic increments only |

**Index:** partial `idx_videos_category_published (category_id, published_at DESC)
WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL` — exactly the rows the related-videos
query reads.

No backfill needed: every existing video starts at 0 views.

### Domain rules

- `Video.viewsCount()` — read-only accessor; the entity never mutates the counter (increments are
  an atomic SQL operation behind the repository port, or concurrent viewers lose updates).
- A view is counted only for **published** videos: the owner previewing a draft is not audience.
- Suggestions obey the Phase 04 visibility matrix: only published + PUBLIC videos are ever
  suggested; the base video itself follows the read rule (draft → 404 for non-owners).

### API Contracts (all under `/api/v1`)

- `GET /videos/{slug}/related?limit` (public) → 200 `[{ id, slug, title, status, visibility,
  publishedAt, thumbnailUrl, durationSeconds, views, createdAt }]`; `limit` default 10, max 20;
  404 unknown slug or draft of another owner. Same category, published + PUBLIC, excluding the
  video itself, ordered by `published_at DESC`. Base video without category → latest published
  PUBLIC videos platform-wide (excluding itself).
- `GET /videos/{slug}/stream` (public) → unchanged contract; issuing the redirect for a
  **published** video increments `views_count` by 1 (single atomic UPDATE, same transaction).
  Draft previews and downloads do not count.
- `views` (long) added to `VideoInfoResponse` (info, PATCH, publish, thumbnail/complete responses)
  and `VideoSummaryResponse` (related, owner panel, public channel listing).

### Repository port additions

```java
// VideoRepository
void incrementViews(UUID id);                                    // atomic UPDATE ... + 1
List<Video> findRelatedByCategory(UUID categoryId, UUID excludeId, int limit); // published+PUBLIC
List<Video> findLatestListed(UUID excludeId, int limit);         // fallback, published+PUBLIC
```

### Authorization Matrix

| Action | Anonymous | Authenticated (non-owner) | Owner |
|---|---|---|---|
| Related videos of a published video | ✔ | ✔ | ✔ |
| Related videos of a draft | ✖ (404) | ✖ (404) | ✔ |
| Accumulate views (stream of published) | ✔ | ✔ | ✔ |
| Accumulate views (stream of own draft) | — | — | ✖ (plays, but does not count) |

---

## Sub-issues

- **SI-05.1 — Domain:** `Video.viewsCount` (constructor + accessor, `initiate` starts at 0);
  `VideoRepository` port gains `incrementViews`/`findRelatedByCategory`/`findLatestListed`.
  Ripple: every `new Video(...)` call site (mapper, test fixtures).
- **SI-05.2 — Flyway V7:** `views_count` column + partial category/published index.
- **SI-05.3 — Persistence:** `VideoEntity.viewsCount`; mapper; `@Modifying` atomic increment;
  related/fallback queries (Pageable-limited, inside the adapter as before).
- **SI-05.4 — Use cases:** view counting wired into the stream path (`GetStreamUrlUseCase` stops
  being read-only or delegates to a small `RegisterViewUseCase`; counted only when
  `isPublished()`); `GetRelatedVideosUseCase` (base-video access check → category query →
  fallback); `views` added to `VideoInfoView`/`VideoSummaryView`.
- **SI-05.5 — Web:** `GET /videos/{slug}/related` in `VideosController` (optional principal, like
  info/stream); `views` in both response DTOs; Postman (request na pasta Videos + teste de views).
  No security change (covered by `GET /api/v1/videos/**` permitAll).
- **SI-05.6 — Tests + docs + DoD:** unit (draft stream não conta; increment é chamada do port;
  related exclui self/draft/unlisted e usa fallback sem categoria; limit clamp), E2E (stream de
  publicado incrementa `views` visível no info; preview de rascunho não; related só mesma
  categoria + PUBLIC), docs (system-design §fluxo, fluxo-upload passo 5/6, progress.md),
  `./gradlew build` verde; smoke da stack compose com o novo fluxo.

## Dependency Map

```
SI-05.1 ── SI-05.2 ── SI-05.3 ── SI-05.4 ── SI-05.5 ── SI-05.6
```

## Deliverables

1. `docs/phases/phase-05-watch/` — context, plan, validation, progress
2. Migration V7: `views_count` + índice parcial de categoria
3. Contagem de views atômica no caminho do stream (só publicados)
4. `GET /videos/{slug}/related` com fallback sem categoria
5. `views` exposto em todas as leituras de vídeo (info + listagens)
6. Unit + Testcontainers E2E; `./gradlew build` verde
