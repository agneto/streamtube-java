# Phase 10 — CDN in Front of the Storage (plan)

## Objective

Public read URLs (stream, download, thumbnails, HLS segments) optionally point at a
token-authenticated, caching CDN edge instead of the storage — an opt-in profile with zero
use-case changes and a working nginx edge in the compose stack.

---

## Technical Specifications

### URL signer

```java
/** secure_link-style token: base64url(md5(expires + uri + secret)), the commercial-CDN scheme. */
public class CdnUrlSigner {
  String sign(String key, long ttlSeconds);                    // https://{base}/{key}?st=..&e=..
  String signDownload(String key, long ttlSeconds, String fn); // ... &dl={fn} (header at the edge)
}
```

Plain class in `infrastructure.storage`, unit-tested against fixed vectors (same md5 input string
the nginx conf declares: `"{expires}{uri}{secret}"`, base64url without padding).

### Storage decorator

`CdnReadStorageDecorator implements StoragePort`, `@Primary` +
`@ConditionalOnProperty("cdn.enabled")`, wrapping the `S3StorageAdapter` bean:

- `presignStream(key)` → `signer.sign(key, readTtl)`
- `presignStream(key, ttl)` → `signer.sign(key, ttl)` (HLS segments keep their 6 h semantics)
- `presignDownload(key, filename)` → `signer.signDownload(...)`
- everything else (uploads, multipart, internal reads, HEAD/GET/put/delete) → delegate.

With `cdn.enabled=false` (default) the decorator is not registered: bit-for-bit today's behavior.

### Config

| Property | Env | Default |
|----------|-----|---------|
| `cdn.enabled` | `CDN_ENABLED` | `false` |
| `cdn.base-url` | `CDN_BASE_URL` | — (required when enabled) |
| `cdn.secret` | `CDN_SECRET` | — (required when enabled) |

Read TTL (1 h) and HLS segment TTL (6 h) reuse the existing settings. `compose.prod.yaml`: when
`CDN_ENABLED=true`, base-url/secret use `${VAR:?}` fail-fast.

### Compose edge (`cdn` service)

nginx:alpine on port **8090**, config mounted from `infra/cdn/nginx.conf`:

```
location / {
  secure_link $arg_st,$arg_e;
  secure_link_md5 "$secure_link_expires$uri$cdn_secret";
  if ($secure_link = "") { return 403; }   # missing/tampered token
  if ($secure_link = "0") { return 410; }  # expired
  proxy_pass http://minio:9000;
  proxy_cache cdn; proxy_cache_key $uri;   # token stripped from the cache key -> real HITs
  proxy_cache_valid 200 7d;
  add_header X-Cache-Status $upstream_cache_status;
  # downloads: Content-Disposition from the (unsigned) dl arg
}
```

`minio-init` gains `mc anonymous set download local/streamtube-videos` (origin read for the edge;
trade-off in ADV-04). Dev compose ships `CDN_ENABLED=true`, `CDN_BASE_URL=http://localhost:8090/streamtube-videos`,
`CDN_SECRET=dev-cdn-secret`.

---

## Sub-issues

- **SI-10.1 — Signer:** `CdnUrlSigner` + properties (`CdnProperties` with fail-fast when enabled
  without base-url/secret); unit tests with fixed token vectors.
- **SI-10.2 — Decorator:** `CdnReadStorageDecorator` (@Primary, conditional) + wiring so the API
  picks it up and the worker never does (worker reads are internal-only, but the decorator is
  harmless there — verify boot both ways).
- **SI-10.3 — Edge:** `infra/cdn/nginx.conf` + `cdn` service in compose (secure_link, cache,
  X-Cache-Status, Content-Disposition), `mc anonymous set download` in minio-init,
  `compose.prod.yaml` CDN block (fail-fast vars, port 8090 behind TLS proxy).
- **SI-10.4 — Tests:** unit (token vectors, decorator delegation matrix), E2E class with
  `cdn.enabled=true` + fake base-url asserting 302/thumbnail/segment URLs carry `st`/`e` on the
  CDN host while upload URLs stay on storage; smoke: stream 302 → CDN URL → 200 via nginx →
  second fetch `X-Cache-Status: HIT`; tampered token 403; expired 410; HLS segment through the
  edge; upload path untouched.
- **SI-10.5 — Docs + DoD:** system-design §6 flips CDN to done (+ §3.1/§5 notes), deploy.md
  (managed-CDN mapping — CloudFront OAC / Bunny token auth —, origin lockdown, purge
  non-issue), GUIA-DE-USO; progress.md; `./gradlew build` verde.

## Dependency Map

```
SI-10.1 ── SI-10.2 ──┬── SI-10.4 ── SI-10.5
           SI-10.3 ──┘
```

## Deliverables

1. `docs/phases/phase-10-cdn/` — context, plan, validation, progress
2. Perfil `CDN_ENABLED` com signer secure_link e decorator sobre o StoragePort
3. Edge nginx real no compose (token 403/410 + cache com HIT verificável)
4. Guia de produção para CDN gerenciada (o mesmo signer, outra config)
5. Unit + E2E + smoke com cache HIT e token adulterado
