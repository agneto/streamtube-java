---
kind: phase
name: phase-10-cdn
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "The signer and the nginx conf must agree on the exact md5 input string ('{expires}{uri}{secret}', uri = path only, no query) and on base64url WITHOUT padding — a one-character mismatch is a blanket 403. Unit-test the signer against vectors computed independently, and smoke-test against the real nginx."
  - id: ADV-02
    text: "The dl (filename) arg stays OUTSIDE the token: including it would break token validation for the same object signed with/without disposition. Sanitize the filename at the edge or the header becomes an injection point."
  - id: ADV-03
    text: "proxy_cache_key must strip st/e, or every URL is a cache MISS and the CDN adds latency instead of removing it. Assert a HIT in the smoke — a silently mis-keyed cache looks identical to a working one."
  - id: ADV-04
    text: "Anonymous-read origin is a real trade-off: anyone reaching MinIO 9000 can bypass token expiry using the unguessable key (same exposure class as UNLISTED links). Document it and the production answers (CloudFront OAC; MinIO: network-restrict 9000 reads to the edge). Never present the token as the only gate."
  - id: ADV-05
    text: "Upload paths (single PUT, multipart parts) and worker internal reads must keep presigned URLs — the decorator delegates them untouched. An accidentally CDN-ified upload URL fails at the edge (nginx only proxies GET) in a confusing way; cover the delegation matrix in unit tests."
  - id: ADV-06
    text: "cdn.enabled=true without base-url/secret must abort at boot (fail-fast), not mint URLs against a null host at request time."
  - id: ADV-07
    text: "The decorator must not change the worker context: its persistence/storage scan picks up infrastructure.storage — verify the worker boots with CDN_ENABLED unset AND set (its internal reads bypass the decorated methods either way)."
---

# Phase 10 — Validation

## Decisions coverage

Extends §6 without reversing anything: authorization stays at URL issuance in the API (§5), bytes
keep flowing outside the API (§3.1 — now optionally through an edge), playlists stay dynamic
through the API (Phase 09). New conventions in context.md: secure_link token format, decorator
over the port, cache-key stripping, origin trade-off, opt-in default-off. No undecided topic
blocks implementation.

## Dependency gaps

None. Builds on the StoragePort read paths (Phases 03/09); independent of multipart (Phase 08 —
upload-side, untouched). No migration, no domain change.

## Verdict

**clean** — ready to implement.
