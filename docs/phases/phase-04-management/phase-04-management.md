# Phase 04 — Video & Channel Management (plan)

## Objective

Deliver video/channel management on the Phase 03 pipeline: categories, full video editing
(title/description/category/custom thumbnail), PUBLIC/UNLISTED visibility with the
draft → publish flow, the owner's management panel listing, channel info editing and the public
channel page — matching the reference project plan's Phase 04, backend side.

---

## Technical Specifications

### Data Model — migration V6

**New table `categories`:**

| Column | Type | Notes |
|--------|------|-------|
| id | uuid PK | |
| name | varchar(60) unique | seeded fixed list |
| slug | varchar(60) unique | for future filtering (Phase 07) |
| created_at | timestamptz | |

Seed (aligned with the reference plan's suggestion sidebar): e.g. Education, Music, Gaming,
Sports, Technology, Entertainment, News, Other. Final list confirmed at implementation time.

**`videos` — new columns:**

| Column | Type | Notes |
|--------|------|-------|
| description | text null | |
| category_id | uuid null FK→categories | plain column, no JPA association |
| visibility | varchar(10) not null default 'PUBLIC' | `PUBLIC` \| `UNLISTED` |
| published_at | timestamptz null | null ⇔ draft |

**Backfill:** `UPDATE videos SET published_at = updated_at WHERE status = 'READY'` — existing
watchable videos stay watchable (visibility already defaults to PUBLIC).

**Indexes:** `(channel_id, published_at DESC)` for panel/public listings; partial index
`WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL` for public listings.

**`channels`:** no schema change (name/nickname/description already exist; nickname already
unique).

### Domain rules

- `Video.publish(now)`: allowed only when `status == READY` (else `VideoStatusConflictException`);
  sets `published_at = now`. Idempotent republish is a no-op.
- `Video.updateDetails(title, description, categoryId, visibility, now)`: title rules as today
  (1–255); description ≤ 5000; visibility must be a valid enum value.
- `Video.isVisibleTo(ownerCheck)` — read rule used by info/stream/download:
  published (any visibility) → public access; draft → owner only.
- `Video.changeThumbnail(key, now)`: only when `READY` (thumbnail generation already happened).
- `Channel.rename(name)`, `Channel.changeNickname(nickname)`: same validation style as
  `updateDescription`; nickname uniqueness enforced at the repository/constraint level (409 on
  race, as per the users-email pattern).

### API Contracts (all under `/api/v1`)

- `GET /categories` (public) → 200 `[{ id, name, slug }]`.
- `PATCH /videos/{id}` (owner) `{ title?, description?, categoryId?, visibility? }` → 200 video
  info; 400 invalid field; 403 not owner; 404 unknown.
- `POST /videos/{id}/publish` (owner) → 200 video info; 422 not READY; 403/404.
- `POST /videos/{id}/thumbnail` (owner) `{ sizeBytes, contentType }` → 201 `{ uploadUrl }`;
  400 non-image or oversized (`THUMB_MAX_SIZE_BYTES`, default 5 MiB); 403/404.
- `POST /videos/{id}/thumbnail/complete` (owner) → 200 video info (new `thumbnailUrl`);
  409 object missing; 403/404; 422 video not READY.
- `GET /channels/me/videos?page&size` (owner) → 200 page envelope of
  `{ id, slug, title, status, visibility, publishedAt, thumbnailUrl, durationSeconds, createdAt }`
  ordered by `created_at DESC` (all statuses/visibilities).
- `PATCH /channels/me` (owner) `{ name?, nickname?, description? }` → 200 channel info;
  409 nickname taken.
- `GET /channels/{nickname}` (public) → 200 `{ id, name, nickname, description, createdAt }`; 404.
- `GET /channels/{nickname}/videos?page&size` (public) → 200 page envelope, **published + PUBLIC
  only**, ordered by `published_at DESC`.

**Page envelope:** `{ items: [...], page, size, totalItems, totalPages }`; `size` capped at 100.

### Visibility enforcement on existing reads

| Video state | `GET /videos/{slug}` (+stream/download) | Listings |
|---|---|---|
| Draft (any status) | owner only (404 for others — no existence leak) | owner panel only |
| Published PUBLIC | public | public listings |
| Published UNLISTED | public (link-only by design) | owner panel only |

### Authorization Matrix

| Action | Anonymous | Authenticated (non-owner) | Owner |
|---|---|---|---|
| List categories / public channel page & videos | ✔ | ✔ | ✔ |
| Watch published video (slug) | ✔ | ✔ | ✔ |
| See draft video / panel listing / edit / publish / thumbnail | ✖ | ✖ | ✔ |
| Edit channel info | ✖ | ✖ | ✔ |

---

## Sub-issues

- **SI-04.1 — Domain:** `Video` new fields + `publish`/`updateDetails`/`changeThumbnail`/read
  rules; `Visibility` enum; `Channel.rename`/`changeNickname`; `Category` entity +
  `CategoryRepository` port; new exceptions (with `DomainErrorType`).
- **SI-04.2 — Flyway V6:** categories table + seed; videos columns + backfill + indexes.
- **SI-04.3 — Persistence:** `CategoryEntity`/repo/adapter; `VideoEntity`/mapper updates;
  paginated queries in `VideoRepository` port + adapter (`findPageByChannelId`,
  `findPublicPageByChannelId` with counts); nickname-conflict translation in the channel adapter.
- **SI-04.4 — Video use cases:** `UpdateVideoDetailsUseCase` (absorbs rename),
  `PublishVideoUseCase`, `InitiateThumbnailUploadUseCase` (image/* + size limit signed),
  `CompleteThumbnailUploadUseCase` (HEAD check + key swap).
- **SI-04.5 — Channel use cases:** `UpdateChannelInfoUseCase` (absorbs description edit),
  `GetPublicChannelUseCase`, `ListChannelVideosUseCase` (public), `ListMyVideosUseCase` (panel);
  `ListCategoriesUseCase`.
- **SI-04.6 — Visibility on reads:** apply the enforcement table to
  `GetVideoInfo`/`GetStreamUrl`/`GetDownloadUrl` (owner resolution via optional principal).
- **SI-04.7 — Web:** `CategoriesController`; `ChannelsController` additions (public page, panel
  listing, extended PATCH); `VideosController` additions (extended PATCH, publish, thumbnail
  endpoints); page-envelope DTO; security allowlist for the new public GETs; Postman folder.
- **SI-04.8 — Tests + DoD:** unit tests per use case (publish rules, visibility matrix, nickname
  conflict, thumbnail validation); E2E: draft→publish→public-page flow, unlisted access,
  panel pagination, channel edit; migration backfill asserted on real Postgres;
  `./gradlew build` green; docs (fluxo/diagrams/report) updated where behavior changed.

## Dependency Map

```
SI-04.1 ── SI-04.2 ── SI-04.3 ─┬─ SI-04.4 ─┬─ SI-04.7 ─┐
                               ├─ SI-04.5 ─┤           ├── SI-04.8
                               └─ SI-04.6 ─┘           │
                                   (uses SI-04.1 rules)┘
```

## Deliverables

1. `docs/phases/phase-04-management/` — context, plan, validation, progress
2. Domain: publication/visibility model, category, channel rename rules
3. Flyway V6 with seed + backfill; persistence + first paginated repository queries
4. Use cases: video editing/publish/custom thumbnail; channel edit; public channel page; panel
5. Web layer: new/extended endpoints under `/api/v1`, page envelope, security allowlist, Postman
6. Visibility enforced across all existing read paths
7. Unit + Testcontainers E2E for the new flows; `./gradlew build` green
