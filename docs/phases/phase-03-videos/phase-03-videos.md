# Phase 03 — Videos (plan)

## Objective

Deliver the video upload + processing pipeline on the Phase 02 base: presigned
uploads, RabbitMQ-driven worker (FFprobe + FFmpeg), streaming/download redirects,
and the video lifecycle — in Clean Architecture, matching the NestJS contract.

---

## Technical Specifications

### Data Model — `videos`

| Column | Type | Notes |
|--------|------|-------|
| id | uuid PK | |
| channel_id | uuid FK→channels | plain column (no JPA association) |
| title | varchar(255) | |
| slug | varchar(16) unique | base64url random |
| status | varchar(20) | five-state enum |
| storage_key | varchar(500) | object key for the video |
| thumbnail_key | varchar(500) null | object key for the thumbnail |
| duration_seconds | double null | from FFprobe |
| metadata | text null | raw FFprobe JSON |
| error_message | text null | last worker error |
| created_at / updated_at | timestamptz | |

**Status lifecycle:** `PENDING_UPLOAD → QUEUED → PROCESSING → READY | ERROR`.

### API Contracts

- `POST /videos` (auth) `{title}` → 201 `{ id, slug, uploadUrl }`.
- `POST /videos/{id}/complete-upload` (auth, owner) → 204; 403 not owner; 404 not found; 409 object missing in storage; 422 wrong status.
- `GET /videos/{slug}` (public) → 200 `{ id, slug, title, status, thumbnailUrl, durationSeconds, channelId, createdAt }`; 404.
- `GET /videos/{slug}/stream` (public) → 302 Location: presigned URL; 404; 422 not ready.
- `GET /videos/{slug}/download` (public) → 302 with content-disposition; 404; 422 not ready.

### Authorization Matrix

| Endpoint | Anonymous | Authenticated | Owner |
|----------|-----------|---------------|-------|
| POST /videos | ✗ | ✓ | — |
| POST /videos/{id}/complete-upload | ✗ | — | ✓ |
| GET /videos/{slug}, /stream, /download | ✓ | ✓ | ✓ |

### Error Catalog (domain → HTTP)

| Exception | HTTP | code |
|-----------|------|------|
| VideoNotFoundException | 404 | VIDEO_NOT_FOUND |
| ForbiddenVideoAccessException | 403 | FORBIDDEN_VIDEO_ACCESS |
| UploadNotCompletedException | 409 | UPLOAD_NOT_COMPLETED |
| VideoStatusConflictException | 422 | VIDEO_STATUS_CONFLICT |
| VideoNotReadyException | 422 | VIDEO_NOT_READY |

### Events / Messages (RabbitMQ)

- Exchange `video.exchange` (direct); routing key `video.process`.
- Queue `video.processing` (durable) with `x-dead-letter-exchange = video.dlx`; DLQ `video.processing.dlq`.
- Payload: `VideoProcessingMessage { videoId: UUID }` (JSON).
- Listener: 3 attempts (Spring AMQP retry), then rejected → DLQ; on terminal failure the video row is set to `ERROR`.

### Worker processing steps

1. Set status `PROCESSING`, load video.
2. Internal presigned GET URL for `storage_key`.
3. `ffprobe` → duration + raw JSON metadata.
4. `ffmpeg` (`thumbnail` filter, scaled) → JPEG bytes.
5. `putObject` thumbnail → `thumbnails/{slug}.jpg`.
6. Set `READY` with duration, thumbnail_key, metadata.
7. On error → `ERROR` with message.

FFmpeg/FFprobe are invoked via `ProcessBuilder` behind a `VideoAnalyzer` port, so the worker use case is unit-testable.

---

## Step Implementations

- **SI-03.1 — Domain & ports:** `Video`, `VideoStatus`, `VideoRepository`; app ports `StoragePort`, `VideoProcessingPublisher`, `SlugGenerator`, `VideoAnalyzer`; video domain exceptions.
- **SI-03.2 — Flyway V4** `videos` table.
- **SI-03.3 — Persistence:** `VideoEntity` + repo + adapter + mapper.
- **SI-03.4 — Storage adapter:** AWS SDK v2, internal + public presigners; put/head/presign.
- **SI-03.5 — Messaging:** RabbitMQ config (exchange/queue/DLQ) + publisher adapter.
- **SI-03.6 — Upload use cases:** InitiateUpload (slug + presigned PUT), CompleteUpload (object check + enqueue + status).
- **SI-03.7 — Read use cases:** GetVideoInfo, GetStreamUrl, GetDownloadUrl.
- **SI-03.8 — Worker:** `ProcessVideoUseCase` + `@RabbitListener` + `FfmpegVideoAnalyzer`; re-enable JPA in worker (VideoEntity only).
- **SI-03.9 — Web:** `VideosController` + DTOs + security allowlist + error mappings.
- **SI-03.10 — Compose:** worker service uses RabbitMQ/MinIO; Dockerfile.worker with ffmpeg.
- **SI-03.11 — Tests + DoD:** unit (use cases incl. worker), integration (MinIO storage adapter via Testcontainers), e2e (video endpoints via Testcontainers Postgres). `./gradlew build` green.

## Dependency Map

```
SI-03.1 ─┬─ SI-03.2 ─ SI-03.3 ─┬─ SI-03.6 ─ SI-03.7 ─ SI-03.9 ─┐
         ├─ SI-03.4 ────────────┼─ SI-03.8 ────────────────────┤── SI-03.11
         └─ SI-03.5 ────────────┘   SI-03.10 ───────────────────┘
```

## Deliverables

1. `docs/phases/phase-03-videos/` — context, plan, validation (clean), progress
2. Domain video model + ports; application use cases (API + worker)
3. Infrastructure: JPA video persistence, S3 storage adapter (two presigners), RabbitMQ publisher + config, FFmpeg analyzer
4. Flyway V4 migration
5. `VideosController` + DTOs + security + error envelope mappings
6. `bootstrap-worker` RabbitMQ listener + processing; `Dockerfile.worker` + compose worker service
7. Unit + Testcontainers integration (MinIO) + e2e tests; `./gradlew build` green
