# Phase 03 — Videos (context)

## Goal

Port the reference NestJS Phase 03 to Java/Spring Boot under Clean Architecture:
large-file video upload via presigned URLs (no file through the API), automatic
background processing (FFprobe metadata + FFmpeg thumbnail) on a RabbitMQ worker,
unique per-video URL (slug), streaming and download via presigned-URL redirects,
and the five-state video lifecycle persisted in PostgreSQL.

## Endpoints to match

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/videos` | authenticated | Initiate upload — create video row, return presigned PUT URL |
| POST | `/videos/{id}/complete-upload` | authenticated (owner) | Verify object in storage, enqueue processing |
| GET | `/videos/{slug}` | public | Video info (incl. thumbnail URL) |
| GET | `/videos/{slug}/stream` | public | 302 → presigned stream URL (ready only) |
| GET | `/videos/{slug}/download` | public | 302 → presigned download URL (ready only) |

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Object storage (MinIO/S3), presigned upload/stream/download | TD-09 (AWS SDK v2, internal + public presigners) |
| Background queue + worker | TD-06 (RabbitMQ / Spring AMQP) + TD-10 (separate worker app) |
| FFprobe metadata + FFmpeg thumbnail | TD-04 (ProcessBuilder behind a port) |
| Unique slug | base64url random (lesson from NestJS) |
| Streaming/download by redirect | TD-09 (302 to presigned URL; no bytes through API) |
| Five-state lifecycle | PENDING_UPLOAD → QUEUED → PROCESSING → READY \| ERROR |
| Videos table FK → channels | TD-05 (Flyway V4) |

## Lessons carried over from the NestJS implementation

- **Two presigners, no host rewriting:** `host` is SigV4-signed, so client-facing URLs are signed against `STORAGE_PUBLIC_URL` and server/worker URLs against `STORAGE_ENDPOINT`. (In NestJS this surfaced as `SignatureDoesNotMatch`.)
- **No unnecessary JPA associations:** `VideoEntity.channel_id` is a plain UUID column (DB-level FK only), so the worker's persistence unit needs only `VideoEntity` — avoiding the "entity metadata for X#y not found" worker boot failure seen in NestJS.

## Out of scope

- Video/channel management, custom thumbnail, visibility, draft→publish (Phase 04+).
