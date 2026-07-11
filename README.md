# StreamTube — Backend (Java 21 / Spring Boot, Clean Architecture)

A Java reimplementation of the StreamTube backend (a video-sharing platform). The complete
reference roadmap (phases 01–07) is delivered, plus three post-1.0 improvements: resumable
multipart upload, HLS adaptive streaming and an optional CDN read profile.

> This is a **separate repository** from the reference NestJS monorepo. It is a from-scratch
> port to Java 21 + Spring Boot using Clean Architecture. The reference NestJS backend stopped
> at Phase 03; everything from Phase 04 on was planned from the reference **project plan**
> (Next/Nest master plan) and, after 07, from the evolution paths in
> [docs/system-design.md](docs/system-design.md).

## Stack

- **Language/Framework:** Java 21 (LTS) + Spring Boot 3.3.x
- **Architecture:** Clean Architecture — `domain` → `application` → `infrastructure` / `web`
- **Build:** Gradle (Kotlin DSL, multi-module, version catalog)
- **Persistence:** Spring Data JPA (Hibernate) + Flyway (PostgreSQL)
- **Messaging:** RabbitMQ (Spring AMQP) — video processing queue + worker (FFmpeg/FFprobe)
- **Storage:** AWS SDK for Java v2 (MinIO locally, S3-compatible), presigned URLs
- **Auth:** Spring Security 6 + JWT (rotating refresh tokens, reuse detection), Argon2
- **Email:** Spring Mail + Thymeleaf (Mailpit in dev)
- **API docs:** springdoc-openapi (Swagger UI) + Postman collection (`docs/postman/`)
- **Rate limiting:** Bucket4j
- **Testing:** JUnit 5 + Mockito + Testcontainers + MockMvc + ArchUnit

See [docs/decisions/technical-decisions-springboot-backend.md](docs/decisions/technical-decisions-springboot-backend.md)
for the full rationale and [docs/system-design.md](docs/system-design.md) for the system view
(components, upload flow, trade-offs, data model).

## Scope

Reference roadmap — **all delivered**:

| Phase | Capability | Status |
|-------|-----------|--------|
| 01 | Base config, Docker Compose infra, health, Flyway | done |
| 02 | Auth & account (register, login, JWT refresh rotation, email verification, password reset) | done |
| 03 | Video upload (presigned), processing worker (ffprobe + thumbnail), streaming/download | done |
| 04 | Video & channel management (categories, editing, visibility, draft→publish, panel, public channel page) | done |
| 05 | Watch page (view counting, same-category suggestions) | done |
| 06 | Social interactions (likes/dislikes, comments with replies, channel subscriptions + feed) | done |
| 07 | Home grid, search by title/channel, production story (`compose.prod.yaml`, [deploy guide](docs/deploy.md)) | done |

Post-1.0 improvements (from the system-design evolution paths):

| Phase | Capability | Release |
|-------|-----------|---------|
| 08 | Resumable multipart upload (per-part retry, resume, server-side completion) | v1.1.0 |
| 09 | HLS adaptive streaming (multi-quality ladder; playlists via API, segments presigned) | v1.2.0 |
| 10 | CDN read profile (`CDN_ENABLED`: token-auth edge + cache; bundled nginx edge in compose) | — |

Each phase ships with its own plan/validation/progress docs under
[docs/phases/](docs/phases/) and was validated by unit tests, Testcontainers E2E and a smoke
run against the full compose stack.

### API surface (all under `/api/v1`)

- **Auth:** register, confirm-email, resend-confirmation, login, refresh, forgot/reset-password, logout, me
- **Videos:** initiate upload (single PUT **or** resumable multipart), complete, info, stream/download (302), HLS playlists, edit, publish, custom thumbnail, related, comments, reactions
- **Channels:** edit mine, owner panel, public page + public videos, subscribe/unsubscribe
- **Social:** comment replies/reactions/deletion, my subscriptions, subscription video feed
- **Discovery:** home grid (`GET /videos`, category filter), search (`GET /search`), categories

Full list with auth rules in [docs/GUIA-DE-USO.md](docs/GUIA-DE-USO.md) §5, live in the Swagger
UI, and runnable in the Postman collection.

### Running

```bash
docker compose up -d --build       # api + worker + postgres + rabbitmq + minio + mailpit + cdn edge
curl http://localhost:8080/        # {"service":"streamtube-api","status":"ok"}
# Swagger UI: http://localhost:8080/swagger-ui.html
# RabbitMQ UI: http://localhost:15673 | MinIO console: http://localhost:9001
# Mailpit: http://localhost:8025    | CDN edge: http://localhost:8090
```

The worker consumes `video.processing`, runs FFprobe/FFmpeg (metadata, thumbnail, HLS ladder)
and marks the video `READY` or `ERROR` (retries ×3 → DLQ). End-to-end walkthrough in
[docs/fluxo-upload-video.md](docs/fluxo-upload-video.md).

```bash
./gradlew spotlessApply build      # format + unit + ArchUnit + Testcontainers E2E (needs Docker)
```

### Production

```bash
docker compose -f compose.yaml -f compose.prod.yaml up -d --build
```

Secrets are fail-fast (`${VAR:?}`), only the API (8080) and storage (9000) stay exposed, Mailpit
is replaced by real SMTP. Environment table, scaling notes, HLS/CDN operations and the
first-deploy checklist: [docs/deploy.md](docs/deploy.md).

## Workflow

Development follows the same pipeline as the reference project:
research → decisions (`docs/decisions/`) → plan (`docs/phases/<phase>/`, with `validation.md`) →
implementation (with `progress.md`) → release (annotated tag, merge to `main` **and** `dev`).

Git Flow: `main` (stable, tagged releases) ← `dev` (integration) ← `feature/*` / `docs/*` /
`release/*`.
