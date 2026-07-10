---
kind: phase
name: phase-09-hls
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Segment URLs must use the HLS TTL (default 6h), not the 1h read TTL: a VOD player fetches the rendition playlist once and plays to the end — a long video would 403 mid-playback with the short TTL."
  - id: ADV-02
    text: "Playlist rewriting must only touch URI lines: every #EXT-X-* tag line passes through verbatim, and relative URIs are resolved against the playlist's own prefix. A rewrite that mangles EXTINF/BYTERANGE breaks players silently."
  - id: ADV-03
    text: "The ladder never upscales: a 480p source gets [480, 360], not [720, ...]. Use scale=-2:h so odd widths (portrait/anamorphic) stay encodable by libx264 (width must be even)."
  - id: ADV-04
    text: "Transcoding multiplies PROCESSING duration. RabbitMQ's consumer ack timeout (default 30 min) will kill the delivery of a very long video mid-transcode and trigger a redelivery loop — set/document the listener/broker timeout expectations in deploy.md within this phase."
  - id: ADV-05
    text: "The worker writes the whole ladder to local disk before uploading: temp dir under java.io.tmpdir, deleted in finally (success AND failure), and the disk-space expectation (~2-3x source size) documented for ops."
  - id: ADV-06
    text: "Views: the master playlist fetch counts (published only, atomic increment); the rendition playlist and segments must NOT count, or one playback becomes N views. /stream keeps its counting for the progressive fallback."
  - id: ADV-07
    text: "hls_master_key is written by the WORKER's persistence unit: the shared VideoEntity/mapper change is what the worker uses — verify the worker context still boots (ddl validate) and that markReady's new parameter ripples through its fixtures."
  - id: ADV-08
    text: "Old READY videos have hlsUrl null — the info contract must make the fallback explicit so the frontend never assumes HLS exists. No backfill migration; manual reprocess documented as an ops action."
---

# Phase 09 — Validation

## Decisions coverage

Extends §6 (evolution path) without reversing any §3.x decision: bytes stay out of the API
(segments presigned; only small playlists flow through), the visibility matrix keeps being
enforced at URL issuance, processing statuses unchanged. New conventions in context.md: ladder
rules, storage layout by convention under `hls/{slug}/`, playlist-through-API pattern, segment
TTL, views on master fetch. No undecided topic blocks implementation.

## Dependency gaps

None. Builds on the worker pipeline (Phase 03), visibility matrix (Phase 04) and view counting
(Phase 05), all shipped. Independent of Phase 08 (multipart) — both paths end at the same
storage key.

## Verdict

**clean** — ready to implement.
