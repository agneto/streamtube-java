# Phase 02 — Auth & Account (plan)

## Objective

Deliver the full authentication/account feature set on the Phase 01 base, in
Clean Architecture, matching the reference NestJS contracts and behavior.

---

## Technical Specifications

### Data Model

**`users`** — id (uuid pk), email (unique, not null), password (not null, hashed),
is_confirmed (bool, default false), created_at, updated_at.

**`channels`** — id (uuid pk), name (varchar 50), nickname (varchar 50 unique),
description (text null), user_id (uuid unique fk→users, not null), created_at,
updated_at. One channel per user, auto-created on registration.

**`refresh_tokens`** — id (uuid pk), user_id (fk→users), family (uuid),
jti (uuid unique), token_hash (varchar, SHA-256 of the raw refresh JWT),
expires_at (timestamptz), revoked_at (timestamptz null), created_at.

**`verification_tokens`** — id (uuid pk), user_id (fk→users),
type (enum: `email_confirmation` | `password_reset`), token_hash (varchar),
expires_at (timestamptz), consumed_at (timestamptz null), created_at.

### API Contracts (selected)

- `POST /auth/register` → 201 `{ id, email }`; 409 if email exists; 400 validation.
- `GET /auth/confirm-email?token=` → 204; 400/410 invalid/expired token.
- `POST /auth/login` → 200 `{ access_token, refresh_token, token_type: "Bearer", expires_in }`; 401 bad creds; 403 email not confirmed.
- `POST /auth/refresh` → 200 new token pair; 401 invalid/expired; reuse → revoke family + 401.
- `POST /auth/forgot-password` → 204 (always, no user enumeration).
- `POST /auth/reset-password` → 204; 400/410 invalid/expired token.
- `POST /auth/logout` → 204 (authenticated; revokes current refresh token).
- `GET /auth/me` → 200 `{ id, email, isConfirmed, channel: { id, nickname, name } }`.

### Authorization Matrix

| Endpoint | Anonymous | Authenticated |
|----------|-----------|---------------|
| register, confirm-email, resend-confirmation, login, refresh, forgot-password, reset-password | ✓ | ✓ |
| logout, me | ✗ | ✓ |
| Swagger UI, /v3/api-docs, /actuator/health, GET / | ✓ | ✓ |

### Error Catalog (domain exception → HTTP)

| Domain exception | HTTP | code |
|------------------|------|------|
| `EmailAlreadyRegisteredException` | 409 | EMAIL_ALREADY_REGISTERED |
| `InvalidCredentialsException` | 401 | INVALID_CREDENTIALS |
| `EmailNotConfirmedException` | 403 | EMAIL_NOT_CONFIRMED |
| `InvalidTokenException` | 400 | INVALID_TOKEN |
| `TokenExpiredException` | 410 | TOKEN_EXPIRED |
| `TokenReuseDetectedException` | 401 | TOKEN_REUSE_DETECTED |
| `UserNotFoundException` | 404 | USER_NOT_FOUND |
| (bean validation) | 400 | VALIDATION_FAILED |

All errors share an envelope: `{ statusCode, code, message, timestamp, path }`.

### Refresh token rotation algorithm (mirrors NestJS)

On `POST /auth/refresh` with a raw refresh JWT:
1. Validate JWT signature/exp; extract `jti`. Compute SHA-256 hash.
2. Look up `refresh_tokens` by hash. Not found → `InvalidTokenException`.
3. If `expires_at < now` → `TokenExpiredException`.
4. If `revoked_at != null` (already rotated):
   - within grace period (`now - revoked_at <= 10s`): return a fresh access token paired with the latest non-revoked token in the same `family` (no new token created).
   - else: **reuse detected** → revoke ALL tokens in the family, throw `TokenReuseDetectedException`.
5. Valid: revoke current token, mint a new refresh JWT in the same `family` (new `jti`), persist its hash, mint a new access token, return both.

### Security

- Stateless `SecurityFilterChain`; `JwtAuthenticationFilter` (OncePerRequestFilter) validates the access JWT and sets the `SecurityContext`.
- Public allowlist as in the matrix; everything else authenticated.
- `Argon2PasswordEncoder` for password hashing (Bouncy Castle).
- Bucket4j filter limiting `/auth/login`, `/auth/register`, `/auth/forgot-password`, `/auth/refresh` per client IP.

---

## Step Implementations

- **SI-02.1 — Domain & ports:** pure entities (User, Channel, RefreshToken, VerificationToken, VerificationTokenType), domain exceptions, repository ports, service ports (PasswordHasher, AccessTokenService, RefreshTokenService, MailSender, NicknameGenerator, Clock). Unit-testable with no Spring.
- **SI-02.2 — Migrations:** `V2__users_and_channels.sql`, `V3__auth_tokens.sql`.
- **SI-02.3 — Persistence adapters:** JPA entities + Spring Data repositories + mappers + port implementations.
- **SI-02.4 — Security infra:** Argon2 hasher, jjwt access-token service, JWT filter, `SecurityConfig`, authenticated-principal plumbing.
- **SI-02.5 — Email:** Thymeleaf templates + `MailSender` adapter (Spring Mail → Mailpit).
- **SI-02.6 — Register + channel auto-create + confirm/resend:** use cases + `AuthController` parts.
- **SI-02.7 — Login + me.**
- **SI-02.8 — Refresh rotation + logout.**
- **SI-02.9 — Forgot/reset password.**
- **SI-02.10 — Rate limiting (Bucket4j).**
- **SI-02.11 — Error advice + OpenAPI envelope.**
- **SI-02.12 — Tests + DoD:** unit (domain/use cases), integration (Testcontainers: repositories, full auth flows), e2e (MockMvc full app). `./gradlew build` green.

## Dependency Map

```
SI-02.1 ─┬─ SI-02.2 ─ SI-02.3 ─┬─ SI-02.6 ─ SI-02.7 ─ SI-02.8 ─ SI-02.9 ─┐
         ├─ SI-02.4 ────────────┤                                         ├─ SI-02.11 ─ SI-02.12
         └─ SI-02.5 ────────────┘            SI-02.10 ────────────────────┘
```

## Deliverables

1. `docs/phases/phase-02-auth/` — context, plan, validation (clean), progress
2. Domain (entities + ports + exceptions), application (use cases)
3. Infrastructure (JPA persistence + security/JWT + Argon2 + mail)
4. Flyway V2 + V3 migrations
5. `AuthController` + DTOs + `@RestControllerAdvice` error envelope + OpenAPI
6. Bucket4j rate limiting
7. Unit + integration (Testcontainers) + e2e tests; `./gradlew build` green
