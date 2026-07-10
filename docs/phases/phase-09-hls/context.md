# Phase 09 — HLS Adaptive Streaming (context)

## Goal

Implement the second evolution recorded in `system-design.md` §6: **HLS com múltiplas qualidades**.
The worker transcodes every new video into an HLS ladder (up to 720p/480p/360p, never upscaling),
and the player picks the rendition that fits the connection — the right consumption story for the
same bad-network users Phase 08 served on the upload side. Progressive playback of the original
MP4 (`/stream`) stays as the fallback and the download source.

## Endpoints to add or change

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/v1/videos/{slug}/hls/master.m3u8` | public | Master playlist; applies the read rule, **counts the view**, rewrites rendition URIs to the API paths below |
| GET | `/api/v1/videos/{slug}/hls/{rendition}/playlist.m3u8` | public | Rendition playlist with every segment line rewritten to a presigned URL |

Behavior changes: `VideoInfoResponse` gains `hlsUrl` (API path of the master; null for videos
processed before this phase — the frontend falls back to `/stream`). Nothing else moves: presigned
segments keep bytes out of the API, `/stream` and `/download` untouched.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Renditions | Ladder derived from the source height at transcode time: ≥720 → 720p+480p+360p; ≥480 → 480p+360p; below → single rendition at source height. Width follows aspect (`scale=-2:h`). H.264 + AAC, TS segments of 6 s — the maximum-compatibility choice. |
| Storage layout | `hls/{slug}/master.m3u8`, `hls/{slug}/{rendition}/playlist.m3u8` + `seg-NNN.ts`. The video row stores only `hls_master_key` (null = no HLS); everything else is convention under the prefix. |
| Playlists through the API, segments direct | HLS + presigned URLs clash: a playlist references dozens of segment files, each needing its own signature. Resolution: the API serves the two playlists (small text), rewriting segment lines to presigned URLs on every request; segment bytes still flow straight from storage. Visibility matrix is enforced where it always was — at URL issuance — and drafts/UNLISTED behave exactly like `/stream`. |
| Segment URL TTL | New `hls.segment-url-ttl-seconds` (default 6 h): a VOD player fetches the rendition playlist once and must be able to play to the end. Not the 1 h read TTL — a 2 h film would 403 mid-play (recorded pitfall). |
| Views | A view = master playlist fetch of a **published** video (same atomic increment, same no-dedup). `/stream` keeps counting for progressive playback — a player uses one path or the other, never both. |
| Old videos | Videos processed before this phase have `hls_master_key = null`: info exposes `hlsUrl: null` and the frontend uses `/stream`. No automatic backfill — reprocessing is a manual ops action (documented), not a migration. |
| Transcode failures | The pipeline semantics are untouched: transcode runs inside the existing PROCESSING window; failure follows the same retry ×3 → DLQ → ERROR path. Temp workspace cleaned in `finally`. |

## Lessons carried over

- Long external work happens **outside transactions** (ProcessVideoUseCase pattern) — the
  transcode extends that block; each status write still commits on its own.
- Worker-only ports (`VideoAnalyzer` precedent) — the transcoder port is wired in `WorkerBeans`,
  never in the API context.
- Video constructor ripple (+1 field): mapper/entity/fixtures in the same slice.
- Ops notes ship with the feature (Phase 08 lifecycle rule precedent): RabbitMQ ack timeout vs
  long transcodes, worker disk space, manual reprocess — all land in `deploy.md` in this phase.

## Out of scope

- DASH, fMP4/CMAF, DRM, per-title encoding ladders, GPU acceleration.
- Live streaming — VOD only.
- Automatic reprocessing/backfill of the pre-HLS catalog.
- CDN in front of the storage (next candidate, §6) — the playlist/URL design already accommodates
  it (swap presigner for CDN-signed URLs later).
- Player work (hls.js adoption is frontend).
