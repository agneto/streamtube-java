# Phase 10 — CDN in Front of the Storage (context)

## Goal

Implement the third evolution of `system-design.md` §6: every **public read URL** (progressive
stream, download, thumbnails, HLS segments) can point at a **CDN edge** instead of the storage,
with token-authenticated, cacheable URLs — cutting latency and origin bandwidth for playback. The
feature is an **opt-in profile** (`CDN_ENABLED`): with it off, nothing changes (presigned S3 URLs
as today); with it on, only the URL issuance changes — authorization stays exactly where it always
was, in the API.

> The compose stack gains a real, working edge: an nginx service implementing the same
> token-URL scheme (`secure_link`) used by commercial CDNs (BunnyCDN, KeyCDN, CDN77), with
> response caching against the MinIO origin. Swapping it for a managed CDN in production is a
> config change, not a code change.

## What changes (and what does not)

| Read path | CDN off (default) | CDN on |
|-----------|-------------------|--------|
| `/stream`, `/download` 302 | presigned S3 URL | `https://{cdn}/{key}?st=<token>&e=<expiry>` |
| Thumbnails in every listing/info | presigned S3 URL | CDN token URL |
| HLS segment lines in rendition playlists | presigned S3 URL (6 h TTL) | CDN token URL (same TTL semantics) |
| Uploads (single PUT, multipart parts) | presigned S3 URL | **unchanged** — CDNs don't take uploads |
| Worker internal reads (ffprobe/transcode) | internal presigned URL | **unchanged** |
| HLS playlists via API, visibility matrix, views | — | **unchanged** — the API still issues every URL |

No new endpoints, no DB migration, no domain change — the first phase where the entire diff is an
adapter + infrastructure.

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| Integration point | A **decorator** over the existing `S3StorageAdapter`, activated by `cdn.enabled`: the three read-URL methods (`presignStream`, `presignStream(ttl)`, `presignDownload`) produce CDN URLs; every other method delegates. Zero use-case changes — the ports design pays off. |
| Token scheme | nginx `secure_link_md5` format: `token = base64url(md5(expires + uri + secret))`, URL `?st={token}&e={expires}`. Chosen because it is the de-facto token-auth scheme of commercial CDNs — the same signer works against BunnyCDN/KeyCDN-style token auth with at most a format tweak. |
| Cacheability | Presigned S3 URLs are uncacheable (every URL is unique). The edge strips `st`/`e` from the cache key (`proxy_cache_key $uri`), so the same segment/thumbnail is a cache HIT across viewers — the actual point of a CDN. |
| Downloads | `Content-Disposition` can't ride an S3 signature through the CDN: the signer appends `dl={filename}` (part of the signed URI? No — args are not in `$uri`; `dl` stays outside the token) and the edge sets the header from `$arg_dl`. |
| Origin protection | The compose edge reads MinIO anonymously (`mc anonymous set download` on the bucket) with the **trade-off documented**: object keys are unguessable (16-char slugs) — the same protection UNLISTED already relies on — and in production a managed CDN + S3 uses OAC/origin-auth, while self-hosted MinIO restricts the origin by network (only the edge reaches 9000 for reads). Recorded honestly as ADV, not hidden. |
| Config | `cdn.enabled` (default false), `cdn.base-url`, `cdn.secret`, TTLs reused from the existing read/segment settings. Prod fail-fast: enabling CDN without base-url/secret aborts. |
| Compose | New `cdn` service (nginx:alpine, port 8090): `secure_link` validation (403 tampered, 410 expired), `proxy_cache` against MinIO, `X-Cache-Status` header exposed for the smoke. Dev stack ships with CDN **on** so the path is exercised daily. |

## Lessons carried over

- Opt-in via property with fail-fast prod validation (`${VAR:?}` pattern from Phase 07).
- Ops/docs ship with the feature (Phases 08–09 precedent): real-CDN mapping (CloudFront/Bunny),
  origin lockdown guidance, cache purge note.
- E2E: profile-toggled Spring context (one test class with `cdn.enabled=true`).

## Out of scope

- Signed cookies, geo-blocking, per-country tokens, multi-CDN.
- CDN for the API itself (JSON is not the bandwidth problem; playlists must stay dynamic).
- Cache invalidation automation — thumbnails change key on custom upload (`-custom`), videos are
  immutable after READY, so purge is a non-issue by design (documented).
- Origin OAC emulation in MinIO (network isolation documented instead).
