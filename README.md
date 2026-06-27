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
| 01 | Base config, Docker Compose infra, health, Flyway | planned |
| 02 | Auth & account (register, login, JWT refresh rotation, email verification, password reset) | planned |
| 03 | Video upload (presigned), processing worker (ffprobe + thumbnail), streaming/download | planned |

Phase 04+ (video/channel management) is out of scope, matching the reference backend.

## Workflow

Development follows the same pipeline as the reference project:
research → decisions (`docs/decisions/`) → plan (`docs/phases/<phase>/`) → implementation (with `progress.md`).

Git Flow: `main` (stable) ← `dev` (integration) ← `feature/*` / `bugfix/*`.
