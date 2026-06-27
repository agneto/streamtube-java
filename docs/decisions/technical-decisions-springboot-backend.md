---
scope_type: project
related_phases: [1, 2, 3]
status: decided
date: 2026-06-27
scope_description: "Stack and architecture decisions for the StreamTube backend reimplemented in Java 21 + Spring Boot with Clean Architecture: framework baseline, layering, build tool, persistence, migrations, message queue, authentication/JWT, password hashing, object storage, worker execution model, email, API docs, rate limiting, validation/error handling, and testing strategy."
---

# Technical Decisions — StreamTube Backend (Java 21 / Spring Boot, Clean Architecture)

This document records the stack and architecture decisions for a **separate**
reimplementation of the StreamTube backend (currently NestJS 11 in the
`mba-ia-greenfield-project` repo) using Java 21 + Spring Boot with Clean
Architecture. Scope mirrors the NestJS backend as it stands after Phase 03:
Phases 01 (base/config/infra), 02 (auth/account), and 03 (video upload &
processing). Phase 04+ is out of scope.

The feature contract to match (same endpoints/behavior):

- **Auth:** `POST /auth/register`, `GET /auth/confirm-email`, `POST /auth/resend-confirmation`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `POST /auth/logout`, `GET /auth/me`
- **Videos:** `POST /videos`, `POST /videos/:id/complete-upload`, `GET /videos/:slug`, `GET /videos/:slug/stream`, `GET /videos/:slug/download`
- **Domain:** user 1:1 channel (channel auto-created on registration), video belongs to channel, five-state video lifecycle, background processing (ffprobe + thumbnail), presigned upload/stream/download.

---

## TD-01: Language & Framework Baseline

**Context:** The target stack is fixed by the request: Java 21 + Spring Boot. The
decision is which Spring Boot generation and Java baseline to pin.

**Options:**

### Option A: Java 21 (LTS) + Spring Boot 3.3.x
- Spring Boot 3.x requires Java 17+, runs great on 21. Jakarta EE 10 namespaces (`jakarta.*`). Virtual threads (Project Loom) available on 21 for blocking I/O.
- **Pros:** Current LTS Java; latest stable Spring Boot line; Jakarta Bean Validation, Spring Security 6, springdoc 2.x all aligned. Virtual threads help the (blocking) presigned-upload completion and mail calls.
- **Cons:** Jakarta namespace differs from older tutorials (minor).

### Option B: Java 17 + Spring Boot 3.2.x
- Older LTS, still supported.
- **Pros:** Slightly more battle-tested ecosystem.
- **Cons:** Misses Java 21 features (virtual threads, pattern matching for switch, record patterns) the request explicitly asks for.

**Recommendation:** **Option A** — the request states Java 21 explicitly; pair it with the latest stable Spring Boot 3.3.x.

**Decision:** A (Java 21 LTS + Spring Boot 3.3.x)

---

## TD-02: Architectural Style (Clean Architecture layering)

**Context:** The request asks for Clean Architecture. The key choice is how strict
the dependency rule is — specifically whether the domain layer stays free of any
framework/persistence annotations.

**Options:**

### Option A: Strict Clean Architecture — pure domain, separate persistence model
- Layers: `domain` (entities, value objects, domain services, repository **ports** as interfaces — no Spring/JPA), `application` (use cases / interactors orchestrating ports), `infrastructure` (JPA adapters with separate `@Entity` persistence models + mappers, RabbitMQ, S3, mail — implements ports), `presentation`/`web` (REST controllers, DTOs, exception handlers). Dependencies point inward only.
- **Pros:** True dependency inversion — domain testable with zero Spring context. Persistence concerns (lazy loading, `@Id` strategy) never leak into business rules. Matches the intent of the exercise.
- **Cons:** Extra mapping layer (domain entity ↔ JPA entity ↔ DTO). More boilerplate (mitigated by MapStruct).

### Option B: Pragmatic — JPA entities double as domain
- `@Entity` classes are the domain model; use cases live in `@Service`.
- **Pros:** Less code, fewer mappers.
- **Cons:** Framework leaks into the domain (annotations, JPA lifecycle); not真 Clean Architecture; harder to unit-test the domain in isolation.

**Recommendation:** **Option A** — the whole point of the request is Clean Architecture; pay the mapping cost (with MapStruct) to keep the domain pure.

**Decision:** A (Strict layering — pure `domain`, `application` use cases over ports, `infrastructure` adapters with separate JPA persistence models + MapStruct mappers, `web` presentation)

---

## TD-03: Build Tool

**Options:**

### Option A: Maven
- **Pros:** Convention-over-configuration, ubiquitous, abundant documentation, Spring Initializr default, deterministic builds.
- **Cons:** XML verbosity; slower than Gradle for large multi-module incremental builds.

### Option B: Gradle (Kotlin DSL)
- **Pros:** Faster incremental builds, concise DSL.
- **Cons:** Steeper learning curve; more variability across versions.

**Recommendation:** **Option A (Maven)** — most conventional and best-documented; build speed is a non-issue at this project size.

**Decision:** A (Maven, multi-module — see TD-10 for the worker module split)

---

## TD-04: Persistence

**Context:** Need a repository implementation behind the domain ports.

**Options:**

### Option A: Spring Data JPA (Hibernate), isolated in infrastructure
- Separate `@Entity` persistence models, `JpaRepository` interfaces, and MapStruct mappers to/from domain. Domain repository ports are implemented by infrastructure adapters that use the Spring Data repositories.
- **Pros:** Standard Spring stack; transaction management via `@Transactional` on use-case boundaries; mature; least surprising.
- **Cons:** Hibernate "magic" (dirty checking, lazy loading) must be contained to infrastructure.

### Option B: jOOQ
- Type-safe SQL DSL.
- **Pros:** Explicit SQL, no ORM lifecycle; arguably cleaner for Clean Arch.
- **Cons:** More verbose; code generation step; less conventional; overkill for this CRUD-ish domain.

**Recommendation:** **Option A** — conventional and well-isolated when persistence entities are kept separate from the domain (TD-02).

**Decision:** A (Spring Data JPA + Hibernate, persistence models separate from domain)

---

## TD-05: Database Migrations

**Context:** The NestJS backend uses versioned TypeORM migrations. Need the Java equivalent (PostgreSQL).

**Options:**

### Option A: Flyway
- SQL-first versioned migrations (`V1__create_users_and_channels.sql`, …).
- **Pros:** Simple, SQL-native, runs at startup, transparent diffs; integrates with Spring Boot autoconfiguration; Testcontainers-friendly.
- **Cons:** No automatic diff generation (write SQL by hand) — acceptable and explicit.

### Option B: Liquibase
- XML/YAML/SQL changelogs with rollback support.
- **Pros:** DB-agnostic changelog, rollbacks.
- **Cons:** More ceremony; abstraction over SQL we don't need (target is fixed Postgres).

**Recommendation:** **Option A (Flyway)** — SQL-first matches the explicit, reviewable migrations the project favors.

**Decision:** A (Flyway; three baseline migrations mirroring the TypeORM ones: users+channels, auth tokens, videos)

---

## TD-06: Message Queue / Background Processing

**Context:** Decouple upload from video processing. The NestJS backend uses BullMQ
(Redis) — Node-specific. A Java-native equivalent is needed. (Discussed and chosen
with the maintainer.)

**Options:**

### Option A: RabbitMQ (Spring AMQP)
- `RabbitTemplate` producer; `@RabbitListener` consumer in a separate worker app. Native retry/backoff and dead-letter routing via `RetryableTopic`-style config / `RepublishMessageRecoverer` + DLQ.
- **Pros:** Purpose-built task/message broker; durable queues; **native retry with backoff and dead-letter queues** (matches BullMQ's `attempts: 3 + exponential backoff + DLQ`); first-class Spring integration; management UI; light footprint.
- **Cons:** New infra service (replaces Redis in Compose).

### Option B: Apache Kafka (Spring for Apache Kafka)
- Event-streaming log; `@KafkaListener`; retry via retry topics + DLT.
- **Pros:** Great for future event-driven fan-out (Phase 06 notifications); massive scale; durable replayable log.
- **Cons:** Not a task queue — retry/backoff are not native (retry-topic pattern); partitions/offsets add complexity unjustified by a low-throughput, one-job-per-upload workload at this stage.

### Option C: PostgreSQL-backed queue (SKIP LOCKED)
- Jobs table polled with `SELECT … FOR UPDATE SKIP LOCKED`.
- **Pros:** No new infra; transactional enqueue.
- **Cons:** Polling latency; reimplements broker features (retry/backoff/DLQ) by hand.

**Recommendation:** **Option A (RabbitMQ)** — best fit for this workload: native retry/backoff + DLQ map directly onto the existing BullMQ semantics, with idiomatic Spring AMQP support. Kafka is a better fit later if the platform becomes event-driven (noted as future scope).

**Decision:** A (RabbitMQ via Spring AMQP; durable `video.processing` queue + `video.processing.dlq`; 3 delivery attempts with exponential backoff, then DLQ → video marked `ERROR`)

---

## TD-07: Authentication & JWT

**Context:** Must replicate the NestJS auth: stateless JWT access tokens + rotating
refresh tokens with reuse detection and a short grace period, plus email
verification and password reset token flows.

**Options:**

### Option A: Spring Security 6 + jjwt (io.jsonwebtoken), custom filter
- A `OncePerRequestFilter` validates the access JWT and populates the `SecurityContext`. Refresh tokens are persisted (hashed) in `refresh_tokens` with `family`/`jti` for rotation + reuse detection, mirroring the NestJS logic.
- **Pros:** Full control over token shape and the rotation/reuse-detection/grace-period algorithm (which is custom anyway); jjwt is a clean, focused JWT library.
- **Cons:** Manual filter wiring (standard Spring Security idiom).

### Option B: Spring Security OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`) with Nimbus
- Built-in JWT decoding/validation via `JwtDecoder`.
- **Pros:** Less custom validation code for the access token; standards-aligned.
- **Cons:** Designed around OAuth2/OIDC conventions; the bespoke refresh-rotation flow still needs custom code; slightly more friction for symmetric HS256 dev secrets.

**Recommendation:** **Option A** — the refresh-token rotation with reuse detection and grace period is custom domain logic regardless; jjwt + a thin filter keeps the access-token path explicit and matches the existing behavior 1:1.

**Decision:** A (Spring Security 6 + jjwt; stateless access JWT via a `OncePerRequestFilter`; persisted hashed refresh tokens with `family`/`jti`, rotation, reuse detection, and grace period mirroring the NestJS implementation; global default-protected endpoints with explicit public allowlist)

---

## TD-08: Password Hashing

**Options:**

### Option A: Argon2 (`Argon2PasswordEncoder`, Spring Security Crypto)
- **Pros:** Parity with the NestJS backend (which uses argon2); memory-hard, modern KDF; available in Spring Security Crypto (requires Bouncy Castle).
- **Cons:** Slightly heavier dependency (Bouncy Castle).

### Option B: BCrypt (`BCryptPasswordEncoder`)
- **Pros:** Spring default, no extra deps.
- **Cons:** Diverges from the reference backend.

**Recommendation:** **Option A (Argon2)** — keep parity with the reference implementation.

**Decision:** A (Argon2id via `Argon2PasswordEncoder`)

---

## TD-09: Object Storage (S3 / MinIO)

**Context:** Same storage model as NestJS: MinIO locally (S3-compatible), presigned
upload/stream/download, server-side `putObject`/`headObject`. The NestJS code hit
two real issues (SigV4 host-as-signed-header and SDK v3 default checksums) — the
Java design must avoid both from the start.

**Options:**

### Option A: AWS SDK for Java v2 (`software.amazon.awssdk:s3` + `s3-presigner`)
- Two `S3Presigner`/`S3Client` setups: an **internal** one (`endpoint = STORAGE_ENDPOINT`, e.g. `minio:9000`) for server-to-storage ops and worker-internal presigned input URLs, and a **public** one (`endpoint = STORAGE_PUBLIC_URL`, e.g. `localhost:9000`) for client-facing presigned URLs. `pathStyleAccessEnabled(true)` for MinIO.
- **Pros:** Official, maintained; presigner is first-class; same conceptual model as the NestJS implementation; lets us sign each URL against the correct host (no origin rewriting).
- **Cons:** Verbose builders (acceptable).

### Option B: MinIO Java SDK
- **Pros:** Tailored to MinIO; simple presigned API.
- **Cons:** Couples to MinIO; production target is S3-compatible, so the AWS SDK is the portable choice.

**Recommendation:** **Option A** — portable to real S3 and mirrors the corrected NestJS design (two clients, sign against the actual host). Java's AWS SDK v2 presigner does **not** inject the CRC32 default-checksum that broke the Node presigned PUT, so no extra checksum config is needed; this will be verified during implementation.

**Decision:** A (AWS SDK for Java v2 — internal + public presigners; path-style; sign each presigned URL against the host the caller will hit)

---

## TD-10: Video Worker Execution Model

**Context:** The architecture (and the NestJS impl) runs the FFmpeg worker as a
separate container/process consuming the queue.

**Options:**

### Option A: Separate Maven module / Spring Boot app (`worker`) consuming RabbitMQ
- Multi-module Maven: `domain`, `application`, `infrastructure`, `bootstrap-api`, `bootstrap-worker`. The worker app has no web server; it wires the RabbitMQ listener, JPA, and storage, and shells out to FFmpeg via `ProcessBuilder`.
- **Pros:** Matches the target architecture; isolates CPU-heavy FFmpeg from the API; independently scalable; FFmpeg installed only in the worker image.
- **Cons:** One more module + Dockerfile.

### Option B: Same app, `@RabbitListener` in the API process
- **Pros:** Simplest setup.
- **Cons:** FFmpeg competes with HTTP handling; FFmpeg in the API image; doesn't match the architecture.

**Recommendation:** **Option A** — mirrors the NestJS worker split and the C4 architecture.

**Decision:** A (separate `bootstrap-worker` Spring Boot app; FFmpeg/ffprobe via `ProcessBuilder`; consumes `video.processing`)

---

## TD-11: Email

**Options:**

### Option A: Spring Mail (`JavaMailSender`) + Thymeleaf templates
- SMTP to Mailpit in dev. HTML emails rendered from Thymeleaf templates (confirmation, password reset).
- **Pros:** Standard Spring; Thymeleaf is the idiomatic Spring templating engine (Handlebars equivalent); Mailpit works unchanged.
- **Cons:** None significant.

### Option B: Spring Mail + Handlebars (jknack)
- **Pros:** Same template syntax as the NestJS impl.
- **Cons:** Less idiomatic in Spring; Thymeleaf integrates more naturally.

**Recommendation:** **Option A** — Thymeleaf is the natural Spring choice; templates re-authored from the existing `.hbs`.

**Decision:** A (Spring Mail + Thymeleaf; Mailpit in dev)

---

## TD-12: API Documentation

**Decision:** **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui`) — generates OpenAPI 3 + Swagger UI from controllers/DTOs, the equivalent of `@nestjs/swagger`. A shared error-envelope schema mirrors the NestJS `ApiErrorEnvelope`.

---

## TD-13: Rate Limiting

**Decision:** **Bucket4j** (token-bucket) applied via a filter/interceptor on auth-sensitive endpoints, mirroring `@nestjs/throttler`. (Alternative considered: Resilience4j RateLimiter — better suited to client-side/service call limiting than per-IP HTTP throttling.)

---

## TD-14: Validation & Error Handling

**Decision:** **Jakarta Bean Validation** (`spring-boot-starter-validation`, Hibernate Validator) on request DTOs (`@Valid`), plus a global `@RestControllerAdvice` that maps domain exceptions and validation failures to the shared error envelope. Domain exceptions are framework-free types in the `domain`/`application` layer; the advice (presentation layer) maps them to HTTP — services never throw HTTP exceptions, mirroring the NestJS rule.

---

## TD-15: Testing Strategy

**Decision:** Test pyramid mirroring the NestJS suffix contract:

- **Unit** (`*Test`) — pure domain/use-case tests, collaborators mocked (Mockito); no Spring context, no I/O.
- **Integration** (`*IT` / `*IntegrationTest`) — real Postgres/RabbitMQ/MinIO via **Testcontainers**; repositories, storage adapter, queue round-trips.
- **E2E** (`*E2ETest`) — full app via `@SpringBootTest(webEnvironment = RANDOM_PORT)` + **MockMvc**/**RestAssured**, real test DB; auth flows and video endpoints end-to-end.

Definition of Done per phase: relevant suite green, full suite green, `mvn -q verify` (compile + tests) exits 0, and static analysis/format check passes (Spotless + Checkstyle).

---

## Decisions Summary

| ID | Topic | Decision |
|----|-------|----------|
| TD-01 | Language & framework | Java 21 LTS + Spring Boot 3.3.x |
| TD-02 | Architecture | Strict Clean Architecture (pure domain, use cases over ports, infra adapters + MapStruct, web presentation) |
| TD-03 | Build tool | Maven (multi-module) |
| TD-04 | Persistence | Spring Data JPA + Hibernate, persistence models separate from domain |
| TD-05 | Migrations | Flyway (SQL-first) |
| TD-06 | Message queue | RabbitMQ (Spring AMQP) — retry/backoff + DLQ |
| TD-07 | Auth & JWT | Spring Security 6 + jjwt; rotating refresh tokens w/ reuse detection + grace period |
| TD-08 | Password hashing | Argon2id |
| TD-09 | Object storage | AWS SDK for Java v2 — internal + public presigners, path-style |
| TD-10 | Worker model | Separate `bootstrap-worker` Spring Boot app; FFmpeg via ProcessBuilder |
| TD-11 | Email | Spring Mail + Thymeleaf (Mailpit dev) |
| TD-12 | API docs | springdoc-openapi |
| TD-13 | Rate limiting | Bucket4j |
| TD-14 | Validation/errors | Jakarta Bean Validation + @RestControllerAdvice → shared error envelope |
| TD-15 | Testing | JUnit 5 + Mockito + Testcontainers + MockMvc/RestAssured |
