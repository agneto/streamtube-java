# Phase 01 — Base Configuration (plan)

## Objective

Deliver a booting Spring Boot (Java 21) Clean Architecture skeleton, built with
Gradle, that connects to PostgreSQL through Flyway, serves a health endpoint and
Swagger UI, and runs in Docker Compose together with PostgreSQL, RabbitMQ, MinIO
and Mailpit — the infrastructure later phases consume.

---

## Technical Specifications

### Module layout (Clean Architecture)

```
streamtube-java/
├── settings.gradle.kts          # includes all modules
├── build.gradle.kts             # root: shared plugins/config via subprojects
├── gradle/libs.versions.toml    # version catalog
├── domain/                      # pure Java — entities, value objects, ports (no Spring)
├── application/                 # use cases / interactors over ports (no web/JPA)
├── infrastructure/              # adapters: JPA, Flyway, (later) S3, RabbitMQ, mail
├── bootstrap-api/               # Spring Boot web app (controllers, config, main)
└── bootstrap-worker/            # Spring Boot worker app (minimal in Phase 01)
```

Dependency direction: `bootstrap-* → infrastructure → application → domain`.

### Configuration (env-driven)

`bootstrap-api/src/main/resources/application.yml` reads:

| Env var | Default (dev) | Purpose |
|---------|---------------|---------|
| `DB_HOST` | `db` | PostgreSQL host (Compose service name) |
| `DB_PORT` | `5432` | |
| `DB_NAME` | `streamtube` | |
| `DB_USERNAME` | `streamtube` | |
| `DB_PASSWORD` | `streamtube` | |
| `SERVER_PORT` | `8080` | API port |

Spring Boot Actuator exposes `/actuator/health`. springdoc serves `/swagger-ui.html` and `/v3/api-docs`.

### Database / migrations

- Flyway runs on startup against PostgreSQL.
- `V1__init.sql` — baseline: enable `pgcrypto` (for `gen_random_uuid()` used by later phases). No business tables yet.

### Health endpoint

- `GET /` → `200` `{"service":"streamtube-api","status":"ok"}` (a thin web-layer controller; the reference NestJS app answers `GET /`).
- `GET /actuator/health` → Spring Boot Actuator health (includes DB).

### Docker Compose services

| Service | Image | Ports | Notes |
|---------|-------|-------|-------|
| `api` | built from `Dockerfile.api` | `8080:8080` | depends on db (healthy) |
| `db` | `postgres:17` | `5432` | healthcheck `pg_isready` |
| `rabbitmq` | `rabbitmq:3-management` | `5672`, `15672` | healthcheck; used from Phase 03 |
| `minio` | `minio/minio` | `9000`, `9001` | healthcheck; used from Phase 03 |
| `minio-init` | `minio/mc` | — | creates `streamtube-videos` bucket |
| `mailpit` | `axllent/mailpit` | `1025`, `8025` | used from Phase 02 |

> RabbitMQ/MinIO/Mailpit are provisioned now so the stack is stable across phases, matching how the reference project front-loads infra.

---

## Step Implementations

### SI-01.1 — Gradle multi-module skeleton
- Root `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`.
- Empty-but-wired modules: `domain`, `application`, `infrastructure`, `bootstrap-api`, `bootstrap-worker` with correct inter-module dependencies.
- Gradle wrapper (8.10.x) committed.
- **Acceptance:** `./gradlew projects` lists all modules; `./gradlew build` compiles.

### SI-01.2 — Spring Boot API boot + health + OpenAPI
- `StreamtubeApiApplication` (`@SpringBootApplication`) in `bootstrap-api`.
- `HealthController` → `GET /`.
- Actuator + springdoc dependencies and config.
- `application.yml` with env-driven datasource and server port.
- **Acceptance:** app starts; `GET /` returns 200 JSON; `/swagger-ui.html` loads.

### SI-01.3 — PostgreSQL + Flyway
- Spring Data JPA + Flyway + PostgreSQL driver in `infrastructure`/`bootstrap-api`.
- `V1__init.sql` baseline migration.
- **Acceptance:** on startup Flyway applies V1; `flyway_schema_history` exists.

### SI-01.4 — Docker Compose + Dockerfile
- `Dockerfile.api` (multi-stage: Gradle build → JRE 21 runtime).
- `compose.yaml` with all services above and healthchecks; service-name networking.
- `.env.example` with dev defaults.
- **Acceptance:** `docker compose up -d` → all services healthy; `curl localhost:8080/` returns 200.

### SI-01.5 — Test foundation
- Unit: `HealthControllerTest` (MockMvc slice, no DB).
- Integration: `ApplicationContextIntegrationTest` using Testcontainers PostgreSQL — boots the context, asserts Flyway ran and `/actuator/health` is UP.
- **Acceptance:** `./gradlew build` runs both green.

### SI-01.6 — Definition of Done
- `./gradlew build` exits 0 (compile + tests).
- Spotless format check passes.
- Compose stack verified up and healthy.

---

## Dependency Map

```
SI-01.1 (skeleton)
  └── SI-01.2 (api boot + health + openapi)
        └── SI-01.3 (postgres + flyway)
              └── SI-01.4 (docker compose)
                    └── SI-01.5 (tests)
                          └── SI-01.6 (DoD)
```

---

## Deliverables

1. `docs/phases/phase-01-base/` — context.md, phase-01-base.md, validation.md, progress.md
2. Gradle multi-module project (5 modules) + wrapper + version catalog
3. `bootstrap-api` Spring Boot app: health endpoint, Actuator, springdoc, env config
4. Flyway baseline migration `V1__init.sql`
5. `Dockerfile.api` + `compose.yaml` (api, db, rabbitmq, minio, minio-init, mailpit) + `.env.example`
6. Unit + Testcontainers integration tests
