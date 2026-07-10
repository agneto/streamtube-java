# phase-09-hls — Progress

**Status:** completed
**SIs:** 7/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-09.1 | Domain (hlsMasterKey + markReady parameter) | done | 4-arg markReady overload kept, so only the worker path ripples |
| SI-09.2 | Flyway V11 (hls_master_key) | done | applied by Testcontainers E2E |
| SI-09.3 | Worker (VideoTranscoder port, ffmpeg ladder, upload, orchestration) | done | ladder unit-tested (never upscales, even heights); temp workspace deleted in finally; 30 min transcode timeout |
| SI-09.4 | API use cases (master + rendition playlists, rewrite, views) | done | HlsPlaylistUseCasesTest: only URI lines rewritten, master counts view (published only), rendition/segments never count, 404 matrix incl. pattern-gated rendition names |
| SI-09.5 | Web (playlist routes, hlsUrl no info, Postman) | done | application/vnd.apple.mpegurl + Cache-Control no-store; no security change |
| SI-09.6 | Tests (unit ladder/rewrite + E2E fake transcoder + smoke real) | done | E2E: visibility matrix on playlists, master counts exactly 1 view, progressive fallback for the old catalog |
| SI-09.7 | Docs + DoD | done | system-design §6 flips HLS to done; deploy.md §5 (ack timeout, disco, reprocesso manual); fluxo-upload + GUIA-DE-USO |

## Notes

- Second post-roadmap improvement (system-design §6): adaptive playback for the same bad-network
  users Phase 08 served on upload.
- Key pattern: playlists (small text) flow through the API — where the visibility matrix and view
  counting already live — while segment bytes stay presigned straight from storage.
- Progressive `/stream` remains the fallback (old catalog has `hlsUrl: null`) and the download
  source.
