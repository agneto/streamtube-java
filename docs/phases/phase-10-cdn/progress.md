# phase-10-cdn — Progress

**Status:** completed
**SIs:** 5/5 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-10.1 | Signer (secure_link token + fail-fast properties) | done | token vector computed independently (python) so the test is not circular with the signer |
| SI-10.2 | Decorator sobre o StoragePort (reads → CDN, resto delega) | done | full delegation matrix unit-tested; @ConditionalOnProperty keeps default behavior bit-for-bit |
| SI-10.3 | Edge nginx no compose (token, cache, disposition) + prod override | done | envsubst template; $uri strips st/e from origin AND cache key; prod gates the edge behind --profile edge |
| SI-10.4 | Tests (vetores, matriz, E2E perfil ligado, smoke HIT/403/410) | done | CdnUrlE2ETest boots with cdn.enabled=true and the REAL storage adapter (presigning is offline) |
| SI-10.5 | Docs + README + DoD | done | README refreshed (was stuck at "phase 04 planned"): phases 01–10, releases, API surface |

## Notes

- Third post-roadmap improvement (system-design §6). First phase with **no migration and no
  domain change** — the whole diff is an adapter and infrastructure, which is exactly what the
  ports design promised.
- Opt-in (`CDN_ENABLED=false` default): with it off, behavior is bit-for-bit today's presigned
  URLs.
- The compose edge (nginx secure_link + proxy_cache) uses the same token scheme as commercial
  CDNs — production migration is config, not code.
