# Phase 01 — Base Configuration (context)

## Goal

Establish the Java/Spring Boot project foundation that all later phases build on:
a Clean Architecture multi-module Gradle project that boots, connects to
PostgreSQL via Flyway-managed migrations, exposes a health endpoint and OpenAPI
docs, and runs entirely in Docker Compose alongside the infrastructure the later
phases need (PostgreSQL, RabbitMQ, MinIO, Mailpit).

This mirrors **Phase 01 of the reference NestJS backend** ("Configuração Base"):
base config, Docker, a health/hello endpoint, and the database connection — no
business domain tables yet (those start in Phase 02).

## Capabilities in scope

| Capability | Source |
|------------|--------|
| Clean Architecture module skeleton (`domain`, `application`, `infrastructure`, `bootstrap-api`, `bootstrap-worker`) | TD-02, TD-10 |
| Gradle multi-module build (Kotlin DSL + version catalog + wrapper) | TD-03 |
| Spring Boot API that boots with a health/hello endpoint | TD-01 |
| PostgreSQL connection + Flyway baseline migration | TD-04, TD-05 |
| OpenAPI / Swagger UI | TD-12 |
| Typed configuration from environment variables, dev profile | TD-01 |
| Docker Compose: API + PostgreSQL + RabbitMQ + MinIO (+bucket init) + Mailpit | TD-06, TD-09, TD-11 |
| Test foundation: context-loads unit test + Testcontainers integration test | TD-15 |

## Out of scope (later phases)

- Domain tables and entities (users, channels, videos) — Phase 02/03
- Auth, JWT, security filters — Phase 02
- Storage adapter, presigned URLs, video endpoints — Phase 03
- The worker's actual FFmpeg processing — Phase 03 (the `bootstrap-worker` module is created but minimal here)

## Conventions to match

- Docker networking uses Compose **service names** as hosts (`db`, `rabbitmq`, `minio`, `mailpit`) — never `localhost` — inside containers.
- Clean Architecture dependency rule: `web/bootstrap → infrastructure → application → domain`; domain has no framework deps.
- Tests: `*Test` (unit, no Spring), `*IntegrationTest`/`*IT` (Testcontainers), `*E2ETest` (full app).
