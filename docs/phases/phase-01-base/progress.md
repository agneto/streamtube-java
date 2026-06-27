# phase-01-base — Progress

**Status:** completed
**SIs:** 6/6 completed

### SI-01.1 — Gradle multi-module skeleton
- **Status:** completed
- **Tests:** n/a (build wiring)
- **Observations:** Five modules wired with Clean Architecture dependency direction (`bootstrap-* → infrastructure → application → domain`). Kotlin DSL, `gradle/libs.versions.toml` version catalog, Gradle wrapper 8.10.2 committed. `./gradlew build` compiles all modules.

### SI-01.2 — Spring Boot API boot + health + OpenAPI
- **Status:** completed
- **Tests:** `HealthControllerTest` (`@WebMvcTest`) — PASS
- **Observations:** `StreamtubeApiApplication` (`scanBasePackages = "com.streamtube"`). `HealthController` → `GET /` returns `{"service":"streamtube-api","status":"ok"}`. Actuator (`/actuator/health`) + springdoc (`/swagger-ui/index.html`, `/v3/api-docs`) enabled. Verified live: `GET /` 200, health UP, swagger 200, api-docs 200.

### SI-01.3 — PostgreSQL + Flyway
- **Status:** completed
- **Tests:** covered by `ApplicationContextIntegrationTest` — PASS
- **Observations:** Spring Data JPA + Flyway + PostgreSQL driver in `infrastructure`. `V1__init.sql` enables `pgcrypto` (no business tables yet — those start in Phase 02). Verified live: `flyway_schema_history` has `1|init|success`, `pgcrypto` extension present.

### SI-01.4 — Docker Compose + Dockerfile
- **Status:** completed
- **Tests:** n/a (infra)
- **Observations:** `Dockerfile.api` (multi-stage: `gradle:8.10.2-jdk21` build → `eclipse-temurin:21-jre` runtime, jar `streamtube-api.jar`). `compose.yaml` with `api`, `db` (postgres:17), `rabbitmq` (3-management), `minio` (+ `minio-init` bucket), `mailpit`; healthchecks; service-name networking. `.env.example` with dev defaults. Verified: image builds, `api` + `db` start healthy, `GET /` 200. (`minio`/`mailpit` also came up healthy; `rabbitmq` host port 5672 only collided with an unrelated local container in the dev machine — not a project issue.)

### SI-01.5 — Test foundation
- **Status:** completed
- **Tests:** `HealthControllerTest` (unit slice), `ApplicationContextIntegrationTest` (Testcontainers PostgreSQL) — 2/2 PASS, 0 skipped
- **Observations:** Integration test boots the full context against real `postgres:17-alpine` via Testcontainers `@ServiceConnection`, asserts Flyway ran and `pgcrypto` exists. Uses `@Testcontainers(disabledWithoutDocker = true)` so the suite stays green where Docker is unavailable (ran for real here — not skipped).

### SI-01.6 — Definition of Done
- **Status:** completed
- **Tests:** full build green
- **Observations:** `./gradlew build` exits 0 (compile + Spotless check + all tests). Spotless format check passes. Compose stack verified up and healthy (api + db). Committed on `feature/phase-01-base`.
