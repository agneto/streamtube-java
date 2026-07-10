# phase-09-hls — Progress

**Status:** not started
**SIs:** 0/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-09.1 | Domain (hlsMasterKey + markReady parameter) | pending | |
| SI-09.2 | Flyway V11 (hls_master_key) | pending | |
| SI-09.3 | Worker (VideoTranscoder port, ffmpeg ladder, upload, orchestration) | pending | |
| SI-09.4 | API use cases (master + rendition playlists, rewrite, views) | pending | |
| SI-09.5 | Web (playlist routes, hlsUrl no info, Postman) | pending | |
| SI-09.6 | Tests (unit ladder/rewrite + E2E fake transcoder + smoke real) | pending | |
| SI-09.7 | Docs + DoD | pending | |

## Notes

- Second post-roadmap improvement (system-design §6): adaptive playback for the same bad-network
  users Phase 08 served on upload.
- Key pattern: playlists (small text) flow through the API — where the visibility matrix and view
  counting already live — while segment bytes stay presigned straight from storage.
- Progressive `/stream` remains the fallback (old catalog has `hlsUrl: null`) and the download
  source.
