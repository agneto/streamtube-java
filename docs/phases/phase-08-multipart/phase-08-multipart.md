# Phase 08 — Multipart Upload (plan)

## Objective

Large files go up in independent, retriable, resumable parts: presigned URL per part, uploaded
state queryable, server-side completion — without touching the single-PUT flow or anything after
`QUEUED`.

---

## Technical Specifications

### Data Model — migration V10

**`videos` — nullable session columns (one active multipart session per video):**

| Column | Type | Notes |
|--------|------|-------|
| upload_id | varchar(200) | storage's multipart uploadId; null = no active session |
| upload_size_bytes | bigint | declared total size (drives totalParts and the final check) |
| upload_part_size | bigint | part size fixed at initiate (config may change later; the session keeps its own) |

No index needed (always accessed through the video row).

### StoragePort additions

```java
String createMultipartUpload(String key, String contentType);              // -> uploadId
String presignUploadPart(String key, String uploadId, int partNumber, long contentLength);
List<UploadedPart> listUploadedParts(String key, String uploadId);         // (partNumber, sizeBytes, etag)
void completeMultipartUpload(String key, String uploadId, List<UploadedPart> parts);
void abortMultipartUpload(String key, String uploadId);
long objectSizeBytes(String key);                                          // final size check
```

`UploadedPart` is a small record in the port package. Part URLs sign the exact Content-Length and
use `upload.part-url-ttl` (default 1 h — retries just re-request).

### Domain rules

- `Video.beginMultipartUpload(uploadId, sizeBytes, partSize, now)` — only while `PENDING_UPLOAD`
  and without an active session (409 `UPLOAD_SESSION_CONFLICT` otherwise);
  `Video.clearUploadSession(now)` on complete/abort; `hasActiveUpload()`.
- `totalParts = ceil(upload_size_bytes / upload_part_size)`; initiate validates
  `totalParts <= 10_000` (S3 limit) → 400 `INVALID_UPLOAD_SIZE` otherwise.
- Part/complete/abort without an active session → 409 `NO_ACTIVE_UPLOAD`.

### API Contracts (all under `/api/v1`, owner-authenticated)

- `POST /videos/multipart` `{title, sizeBytes, contentType}` → 201
  `{id, slug, partSizeBytes, totalParts}` — same title/type/size validation as the single PUT.
- `POST /videos/{id}/parts` `{partNumbers: [1,4,7]}` → 200
  `[{partNumber, url, contentLengthBytes}]`; 400 for numbers outside `1..totalParts`
  (max 100 numbers per call).
- `GET /videos/{id}/parts` → 200 `{partSizeBytes, totalParts, uploaded: [{partNumber,
  sizeBytes}]}` — straight from `ListParts` (resume).
- `POST /videos/{id}/complete-multipart` → 204. Server: ListParts → validate (all `totalParts`
  present, sizes coherent) → CompleteMultipartUpload → HEAD size == declared (mismatch: object
  deleted + 409 `UPLOAD_NOT_COMPLETED`) → clear session → `markQueued` → publish job **after
  commit** (reuses the complete-upload tail). Missing parts → 409 `UPLOAD_NOT_COMPLETED` with the
  missing count in the message.
- `DELETE /videos/{id}/multipart` → 204. AbortMultipartUpload + clear session; video stays
  `PENDING_UPLOAD` (a new `POST /videos/multipart`-style session can be reopened on the same
  video via... it cannot: re-initiation creates a new video, same as the single-PUT flow today).

### Config

| Property | Env | Default |
|----------|-----|---------|
| `upload.part-size-bytes` | `UPLOAD_PART_SIZE_BYTES` | 8 MiB (min 5 MiB) |
| `upload.part-url-ttl-seconds` | `UPLOAD_PART_URL_TTL_SECONDS` | 3600 |

---

## Sub-issues

- **SI-08.1 — Domain:** session fields + `beginMultipartUpload`/`clearUploadSession`/
  `hasActiveUpload`; exceptions `NO_ACTIVE_UPLOAD` (CONFLICT), `UPLOAD_SESSION_CONFLICT`
  (CONFLICT). Constructor ripple (mapper, entity, fixtures) fixed here.
- **SI-08.2 — Flyway V10:** the 3 nullable columns.
- **SI-08.3 — Storage:** `StoragePort` additions + `S3StorageAdapter` (SDK: create/list/
  complete/abort multipart, presigned `UploadPartRequest` with signed length, HEAD size);
  `UploadedPart` record.
- **SI-08.4 — Use cases:** `InitiateMultipartUploadUseCase`, `IssuePartUrlsUseCase`,
  `ListUploadedPartsUseCase`, `CompleteMultipartUploadUseCase` (shares the QUEUED+publish tail
  with `CompleteUploadUseCase` — extract the common collaborator), `AbortMultipartUploadUseCase`.
- **SI-08.5 — Web:** routes on `VideosController` (all authenticated, no security change);
  DTOs; Postman folder "Upload multipart" (initiate → parts → PUT → status → complete).
- **SI-08.6 — Tests:** unit (session rules, part-number validation, complete with missing parts,
  size mismatch aborts) + E2E with the fake storage extended (in-memory parts map); compose smoke
  uploading a real ~20 MiB video in 3 parts, interrupting and resuming via `GET /parts`, then
  READY → publish → stream.
- **SI-08.7 — Docs + DoD:** system-design §3.2 flips to "implemented" (comparison table stays as
  rationale), fluxo-upload gains the multipart branch, deploy.md gains the lifecycle rule for
  incomplete multipart uploads, GUIA-DE-USO table; `./gradlew build` verde; progress.md.

## Dependency Map

```
SI-08.1 ── SI-08.2 ── SI-08.3 ── SI-08.4 ── SI-08.5 ── SI-08.6 ── SI-08.7
```

## Deliverables

1. `docs/phases/phase-08-multipart/` — context, plan, validation, progress
2. Migration V10: sessão multipart no vídeo
3. Upload em partes com URLs por parte re-emissíveis e **resume** (`GET /parts`)
4. Conclusão server-side (cliente nunca vê ETag) com verificação de tamanho
5. Abort + regra de lifecycle documentada para sessões abandonadas
6. Unit + Testcontainers E2E + smoke real com upload interrompido/retomado
