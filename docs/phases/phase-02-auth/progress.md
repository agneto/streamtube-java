# phase-02-auth — Progress

**Status:** completed
**SIs:** 12/12 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-02.1 | Domain & ports | completed | covered by use-case unit tests |
| SI-02.2 | Flyway migrations (users+channels, auth tokens) | completed | verified by AuthE2ETest (real Postgres) |
| SI-02.3 | Persistence adapters (JPA + mappers) | completed | AuthE2ETest |
| SI-02.4 | Security infra (Argon2, JWT, filter, config) | completed | AuthE2ETest (login/me/401) |
| SI-02.5 | Email (Thymeleaf + Spring Mail) | completed | captured via test MailSender |
| SI-02.6 | Register + channel auto-create + confirm/resend | completed | AuthE2ETest.registerConfirmLoginMeFlow |
| SI-02.7 | Login + me | completed | AuthE2ETest |
| SI-02.8 | Refresh rotation + logout | completed | RefreshTokensUseCaseTest (5) + AuthE2ETest.refreshRotatesTokens |
| SI-02.9 | Forgot/reset password | completed | use cases implemented; opaque token + hash |
| SI-02.10 | Rate limiting (Bucket4j) | completed | RateLimitingFilter on auth POST endpoints |
| SI-02.11 | Error advice + OpenAPI envelope | completed | GlobalExceptionHandler + ErrorEnvelope |
| SI-02.12 | Tests + DoD | completed | `./gradlew build` green |

## Notes

- **Clean Architecture:** `domain` is framework-free (pure entities + ports + exceptions). `application` holds use cases (Spring stereotypes for wiring only). `infrastructure` has JPA persistence (entities/repos/adapters + hand-written mappers), Argon2 hashing, jjwt access tokens, opaque refresh/verification tokens, Spring Mail + Thymeleaf. `web` (bootstrap-api) has the controller, DTOs, security config/filters, and the error advice.
- **Tokens:** access = HS256 JWT; refresh & verification = opaque 256-bit secrets stored as SHA-256 hashes. Refresh rotation implements reuse detection + 10s grace period (see decisions TD-07 implementation notes).
- **Migrations:** `V2__users_and_channels.sql`, `V3__auth_tokens.sql` (on top of Phase 01 `V1__init.sql`).
- **Endpoints:** register, confirm-email, resend-confirmation, login, refresh, forgot-password, reset-password, logout, me — matching the reference NestJS contract; token JSON fields are snake_case (`access_token`, `refresh_token`, `token_type`, `expires_in`).

## Definition of Done

- `./gradlew build` exits 0 — compile + Spotless + tests.
- Tests: **12 green, 0 skipped** — application `RefreshTokensUseCaseTest` (5, rotation/reuse/grace/expired/invalid); bootstrap-api `AuthE2ETest` (5, full HTTP flow vs real Postgres), `ApplicationContextIntegrationTest` (1), `HealthControllerTest` (1).
- Built on `feature/phase-02-auth` (from `dev`).
