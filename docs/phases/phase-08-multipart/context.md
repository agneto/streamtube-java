# Phase 08 — Multipart Upload (context)

## Goal

Implement the evolution recorded in `system-design.md` §3.2: **S3 Multipart Upload** for large
videos — the file goes up in independent parts (default 8 MiB), each retriable on its own, with
**resume**: a client on a bad connection re-requests the URL of a failed part or reboots and asks
"which parts already made it?" instead of restarting a multi-GB upload from zero. First
post-roadmap improvement phase (the reference plan ended at Phase 07).

> The single-PUT flow stays untouched — it remains the right choice for small files. Multipart is
> a second, explicit initiation path the client picks when the file is big.

## Endpoints to add

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/v1/videos/multipart` | user | Create the video (draft) + open the multipart session; returns `{id, slug, partSizeBytes, totalParts}` |
| POST | `/api/v1/videos/{id}/parts` | owner | Issue presigned URLs for the requested `partNumbers` (re-issuable anytime — retry path) |
| GET | `/api/v1/videos/{id}/parts` | owner | Parts already in storage (`[{partNumber, sizeBytes}]`) — the resume path |
| POST | `/api/v1/videos/{id}/complete-multipart` | owner | Server assembles the object (ListParts → Complete), verifies total size, `QUEUED` + job after commit |
| DELETE | `/api/v1/videos/{id}/multipart` | owner | Abort: discards uploaded parts, clears the session (video stays `PENDING_UPLOAD`) |

No behavior change to `POST /videos` (single PUT), `complete-upload`, processing or anything
downstream — after `QUEUED` the pipeline cannot tell how the bytes arrived.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Who holds the ETags | **The server** — `complete-multipart` reads the uploaded parts from storage (`ListParts`) instead of taking `{partNumber, etag}` from the client. The client never juggles ETags, no `ExposeHeaders: ETag` CORS dependency, and losing local state costs nothing (resume via `GET /parts`). |
| Part size | Server-dictated: `UPLOAD_PART_SIZE_BYTES` (default 8 MiB; S3 minimum is 5 MiB for every part but the last). `totalParts = ceil(sizeBytes / partSize)`, capped by S3's 10 000-part limit (validation on initiate). |
| Integrity | Each part URL signs its **exact** Content-Length (parts = partSize, last = remainder) — same tamper-proofing as the single PUT. Belt-and-braces: after `CompleteMultipartUpload`, the API HEADs the object and compares the size with the declared `sizeBytes`; mismatch → the object is removed and complete fails 409. |
| Session state | Three nullable columns on `videos` (V10): `upload_id`, `upload_size_bytes`, `upload_part_size` — one active session per video, cleared on complete/abort. No new table: the session's lifecycle is the video's own `PENDING_UPLOAD` window. |
| Part URL TTL | New `upload.part-url-ttl` (default 1 h, vs 15 min of the single PUT) — slow connections take long per part; expired URLs are simply re-requested via `POST /parts`. |
| Validation | Same rules as the single PUT initiate (`video/*`, `UPLOAD_MAX_SIZE_BYTES`); `partNumbers` must be within `1..totalParts`; part/complete/abort endpoints answer 409 `NO_ACTIVE_UPLOAD` when there is no open session. |
| Orphan cleanup | Abandoned sessions (initiate and vanish) hold invisible part bytes in the bucket. Documented ops answer: bucket lifecycle rule aborting incomplete multipart uploads after N days (`mc ilm` for MinIO, lifecycle config for S3) — recorded in `deploy.md`, no cron in the app (same trade-off as orphaned `PENDING_UPLOAD` rows, §3.3). |
| Client flow | initiate multipart → for each part: `POST /parts` (batch) → PUT bytes → on failure/reboot: `GET /parts` to see what survived, re-request URLs only for what's missing → `complete-multipart`. |

## Lessons carried over

- Job published **after commit** (AfterCommitExecutor) — complete-multipart reuses the exact
  complete-upload tail (QUEUED + publish), including its idempotency/conflict semantics.
- Declared size signed into URLs (Phase 03 hardening) — now per part.
- New domain exceptions carry `DomainErrorType`; handler untouched.
- Video constructor ripple (3 new fields): mapper/entity/fixtures fixed in the same slice.
- E2E fake storage grows the multipart methods; the compose smoke uses a real multi-part file.

## Out of scope

- Parallel-part orchestration, progress UI, checksum-per-part (CRC32/SHA) — client concerns.
- Automatic switch of `POST /videos` to multipart by size — the client picks the path.
- Orphan-cleanup job inside the app (lifecycle rule documented instead).
- Any change to processing, publication or social features.
