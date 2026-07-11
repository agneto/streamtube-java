# phase-10-cdn — Progress

**Status:** not started
**SIs:** 0/5 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-10.1 | Signer (secure_link token + fail-fast properties) | pending | |
| SI-10.2 | Decorator sobre o StoragePort (reads → CDN, resto delega) | pending | |
| SI-10.3 | Edge nginx no compose (token, cache, disposition) + prod override | pending | |
| SI-10.4 | Tests (vetores de token, matriz de delegação, E2E perfil ligado, smoke HIT/403/410) | pending | |
| SI-10.5 | Docs + DoD | pending | |

## Notes

- Third post-roadmap improvement (system-design §6). First phase with **no migration and no
  domain change** — the whole diff is an adapter and infrastructure, which is exactly what the
  ports design promised.
- Opt-in (`CDN_ENABLED=false` default): with it off, behavior is bit-for-bit today's presigned
  URLs.
- The compose edge (nginx secure_link + proxy_cache) uses the same token scheme as commercial
  CDNs — production migration is config, not code.
