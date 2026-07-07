# StreamTube — Backend (Java 21 / Spring Boot, Clean Architecture)

A Java reimplementation of the StreamTube backend (a video-sharing platform),
mirroring the feature set of the reference NestJS backend through **Phase 03**
(base/config/infra, auth/account, video upload & processing).

> This is a **separate repository** from the reference NestJS monorepo. It is a
> from-scratch port to Java 21 + Spring Boot using Clean Architecture.

## Stack

- **Language/Framework:** Java 21 (LTS) + Spring Boot 3.3.x
- **Architecture:** Clean Architecture — `domain` → `application` → `infrastructure` / `web`
- **Build:** Gradle (Kotlin DSL, multi-module, version catalog)
- **Persistence:** Spring Data JPA (Hibernate) + Flyway (PostgreSQL)
- **Messaging:** RabbitMQ (Spring AMQP) — video processing queue + worker
- **Storage:** AWS SDK for Java v2 (MinIO locally, S3-compatible)
- **Auth:** Spring Security 6 + JWT (rotating refresh tokens, reuse detection), Argon2
- **Email:** Spring Mail + Thymeleaf (Mailpit in dev)
- **API docs:** springdoc-openapi (Swagger UI)
- **Rate limiting:** Bucket4j
- **Testing:** JUnit 5 + Mockito + Testcontainers + MockMvc/RestAssured

See [docs/decisions/technical-decisions-springboot-backend.md](docs/decisions/technical-decisions-springboot-backend.md)
for the full rationale (options, trade-offs, decision per topic).

## Scope

| Phase | Capability | Status |
|-------|-----------|--------|
| 01 | Base config, Docker Compose infra, health, Flyway | done |
| 02 | Auth & account (register, login, JWT refresh rotation, email verification, password reset) | done |
| 03 | Video upload (presigned), processing worker (ffprobe + thumbnail), streaming/download | done |
| 04 | Video & channel management (categories, editing, visibility, draft→publish, panel, public channel page) | planned — [plan](docs/phases/phase-04-management/phase-04-management.md) |

Phase 05+ (watch page, social interactions, home/search) remains out of scope for now. The
reference NestJS backend stopped at Phase 03, so Phase 04 was planned from the reference
project plan (Next/Nest master plan).

### Endpoints

- **Auth:** `POST /api/v1/auth/register`, `GET /api/v1/auth/confirm-email`, `POST /api/v1/auth/resend-confirmation`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`
- **Videos:** `POST /api/v1/videos`, `POST /api/v1/videos/{id}/complete-upload`, `GET /api/v1/videos/{slug}`, `GET /api/v1/videos/{slug}/stream` (302), `GET /api/v1/videos/{slug}/download` (302)

### Running

```bash
docker compose up -d --build       # api + worker + postgres + rabbitmq + minio + mailpit
curl http://localhost:8080/        # {"service":"streamtube-api","status":"ok"}
# Swagger UI: http://localhost:8080/swagger-ui.html
# RabbitMQ UI: http://localhost:15673  | MinIO console: http://localhost:9001 | Mailpit: http://localhost:8025
```

The `video-worker` consumes the `video.processing` queue, runs FFprobe/FFmpeg, and
updates the video to `READY` (thumbnail + duration + metadata) or `ERROR`.

### Production profile

Run with `SPRING_PROFILES_ACTIVE=prod` outside local dev. The prod profile has **no fallback
values** for credentials/secrets (`DB_*`, `RABBITMQ_*`, `STORAGE_*`, `JWT_SECRET`, `MAIL_*`,
`APP_BASE_URL`): a missing variable — or a `JWT_SECRET` still set to the committed dev value —
aborts startup instead of silently running with dev defaults.

## Workflow

Development follows the same pipeline as the reference project:
research → decisions (`docs/decisions/`) → plan (`docs/phases/<phase>/`) → implementation (with `progress.md`).

Git Flow: `main` (stable) ← `dev` (integration) ← `feature/*` / `bugfix/*`.
