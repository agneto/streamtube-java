# phase-03-videos — Progress

**Status:** completed
**SIs:** 11/11 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-03.1 | Domain & ports | completed | covered by use-case tests |
| SI-03.2 | Flyway V4 (videos) | completed | VideosE2ETest (real Postgres) |
| SI-03.3 | Persistence (VideoEntity + adapter) | completed | VideosE2ETest |
| SI-03.4 | Storage adapter (AWS SDK v2, 2 presigners) | completed | S3StorageAdapterIntegrationTest (MinIO) |
| SI-03.5 | Messaging (RabbitMQ publisher + config) | completed | VideosE2ETest (publish captured) |
| SI-03.6 | Upload use cases (initiate/complete) | completed | VideosE2ETest |
| SI-03.7 | Read use cases (info/stream/download) | completed | VideosE2ETest |
| SI-03.8 | Worker (listener + ffmpeg analyzer) | completed | ProcessVideoUseCaseTest |
| SI-03.9 | Web (VideosController + security) | completed | VideosE2ETest |
| SI-03.10 | Compose worker + Dockerfile.worker | completed | stack smoke test |
| SI-03.11 | Tests + DoD | completed | `./gradlew build` green |

## Notes

- **Clean Architecture:** `domain` adds `Video`/`VideoStatus`/`VideoRepository` + video exceptions (framework-free). `application` adds the use cases (`InitiateUpload`, `CompleteUpload`, `GetVideoInfo`, `GetStreamUrl`, `GetDownloadUrl` as `@Service`; `ProcessVideoUseCase` is a worker-only bean since it depends on the worker's `VideoAnalyzer` port). `infrastructure` adds the JPA `VideoEntity`/adapter, the S3 storage adapter (two presigners), RabbitMQ config + publisher, and the slug generator. `web` adds `VideosController`. The worker app adds the `@RabbitListener` + `FfmpegVideoAnalyzer` (ProcessBuilder).
- **Storage (lesson applied):** two presigners — client-facing URLs signed against `STORAGE_PUBLIC_URL`, server/worker URLs against `STORAGE_ENDPOINT`. The MinIO Testcontainers test performs a real presigned PUT then GET over HTTP, proving the signature is valid (the Java counterpart of the NestJS `SignatureDoesNotMatch` fix).
- **Worker entity graph (lesson applied):** `VideoEntity.channel_id` is a plain UUID column (no JPA association), and the entities are association-free, so the worker boots without the "entity metadata not found" failure seen in NestJS.
- **Queue:** durable `video.processing` with dead-letter exchange → `video.processing.dlq`; listener retries 3× then routes to the DLQ; a DLQ listener marks the video `ERROR`.
- **Migration:** `V4__videos.sql` (on top of V1–V3). Status lifecycle `PENDING_UPLOAD → QUEUED → PROCESSING → READY | ERROR`.
- **Endpoints:** `POST /videos`, `POST /videos/{id}/complete-upload`, `GET /videos/{slug}`, `GET /videos/{slug}/stream` (302), `GET /videos/{slug}/download` (302) — matching the NestJS contract.

## Definition of Done

- `./gradlew build` exits 0 — compile + Spotless + tests.
- Tests: **22 green, 0 skipped** across the project. Phase 03 additions: `ProcessVideoUseCaseTest` (3), `S3StorageAdapterIntegrationTest` (3, MinIO Testcontainers), `VideosE2ETest` (4, real Postgres).
- `Dockerfile.worker` (ffmpeg) + `compose.yaml` worker service; RabbitMQ host ports offset to 5673/15673 to coexist with other local brokers.
- Built on `feature/phase-03-videos` (from `dev`).
