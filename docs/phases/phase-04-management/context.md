# Phase 04 — Video & Channel Management (context)

## Goal

Implement the backend of the reference project's Phase 04 ("Gerenciamento de Vídeos e Canal",
`project-plan.md` of the Next/Nest reference): video categories, full video editing (title,
description, category, custom thumbnail), PUBLIC/UNLISTED visibility, the draft → publish flow,
the channel management panel listing, channel info editing (nickname/name/description) and the
public channel page — in Clean Architecture, under `/api/v1`.

> The reference **NestJS backend** stopped at Phase 03; this phase is charted from the reference
> **project plan** (master plan of the Next/Nest project), not from an existing backend contract.
> Frontend-only items of the reference phase (panel UI screens) become the REST endpoints that
> would power them.

## Endpoints to add or change

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/categories` | public | List platform video categories (seeded) |
| PATCH | `/api/v1/videos/{id}` | owner | **Extend** existing rename: title, description, categoryId, visibility |
| POST | `/api/v1/videos/{id}/publish` | owner | Draft → published (requires status READY) |
| POST | `/api/v1/videos/{id}/thumbnail` | owner | Presigned PUT URL for a custom thumbnail (image/*) |
| POST | `/api/v1/videos/{id}/thumbnail/complete` | owner | Verify object exists, swap `thumbnail_key` |
| GET | `/api/v1/channels/me/videos` | owner | Management panel: paginated, **all** statuses/visibilities |
| PATCH | `/api/v1/channels/me` | owner | **Extend** existing description edit: name, nickname |
| GET | `/api/v1/channels/{nickname}` | public | Public channel page info |
| GET | `/api/v1/channels/{nickname}/videos` | public | Paginated, published + PUBLIC videos only |

Behavior changes to existing endpoints: `GET /videos/{slug}`, `/stream`, `/download` start
enforcing publication/visibility (see rules below).

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Publication model | Processing lifecycle stays untouched (`PENDING_UPLOAD…READY\|ERROR`). Publication is orthogonal: `published_at timestamptz null` (draft ⇔ null) + `visibility` (`PUBLIC` \| `UNLISTED`). `publish()` is a domain rule: only allowed when status is `READY`. |
| Visibility semantics | `PUBLIC`: listed everywhere. `UNLISTED`: reachable by slug (info/stream/download) but never returned by listings. Drafts: owner-only on every read path. |
| Custom thumbnail | Same presigned pattern as the video upload (bytes never through the API): presign with content-type `image/*` + size limit signed into the URL, then a complete step that HEAD-checks and swaps `thumbnail_key`. `StoragePort` needs no change. |
| Categories | Fixed platform list seeded by Flyway (no admin CRUD). `videos.category_id` nullable FK. |
| Pagination | First paginated endpoints of the project. Convention: `?page=0&size=20` (max 100), response envelope `{ items, page, size, totalItems, totalPages }`. Repository ports get explicit page params (no Spring `Pageable` in the application layer). |
| Nickname change | Allowed, uniqueness revalidated (same retry-free path as description edit; race backstopped by the unique constraint → 409). Old public URLs simply break — no redirect (recorded as accepted trade-off). |
| Backfill | Existing `READY` videos predate the publication concept. Migration V6 backfills `published_at = updated_at` and `visibility = 'PUBLIC'` for `READY` rows so nothing already watchable disappears. |

## Lessons carried over

- Keep `category_id` a plain UUID column (DB-level FK only, no JPA association) — same rationale
  as `videos.channel_id` (worker persistence unit stays minimal).
- Declared size/content-type signed into presigned PUT URLs (Phase 03 hardening) applies to
  thumbnails as well.
- Every new domain exception carries a `DomainErrorType`; the handler switch is exhaustive.

## Out of scope

- Video deletion (not in the reference Phase 04 list; storage-cleanup semantics deserve their own slice).
- View/like/comment counts in the panel listing (Phases 05–06 add the data; the DTO gains the fields then).
- Suggestions/search/home listings (Phases 05 and 07), social interactions (Phase 06).
- Channel avatar/banner upload (not in the reference Phase 04 list).
