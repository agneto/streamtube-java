# Phase 02 — Auth & Account (context)

## Goal

Port the reference NestJS Phase 02 (Cadastro, Login e Gerenciamento de Conta) to
Java/Spring Boot under Clean Architecture: user registration with an
auto-created channel, email confirmation, login issuing JWT access + rotating
refresh tokens (with reuse detection and a grace period), password reset, logout,
and a "current user" endpoint — all default-protected by Spring Security with an
explicit public allowlist, plus rate limiting on auth-sensitive endpoints.

## Endpoints to match (same contract as NestJS)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/auth/register` | public | Create user (+ channel), send confirmation email |
| GET | `/auth/confirm-email?token=` | public | Confirm email |
| POST | `/auth/resend-confirmation` | public | Resend confirmation email |
| POST | `/auth/login` | public | Issue access + refresh tokens |
| POST | `/auth/refresh` | public | Rotate refresh token (reuse detection + grace) |
| POST | `/auth/forgot-password` | public | Send reset email |
| POST | `/auth/reset-password` | public | Reset password with token |
| POST | `/auth/logout` | authenticated | Revoke refresh token |
| GET | `/auth/me` | authenticated | Current user info |

## Capabilities & decisions

| Capability | Decision |
|------------|----------|
| User 1:1 Channel, channel auto-created at registration with generated nickname | TD-02 (domain), TD-04 |
| JWT access token + rotating refresh token (family/jti, reuse detection, grace period) | TD-07 |
| Password hashing | TD-08 (Argon2id) |
| Email confirmation + password reset (hashed tokens) | TD-11 (Spring Mail + Thymeleaf) |
| Default-protected endpoints + public allowlist | TD-07 (Spring Security 6) |
| Rate limiting on auth endpoints | TD-13 (Bucket4j) |
| Migrations (users+channels, auth tokens) | TD-05 (Flyway) |
| Error envelope + validation | TD-14 |

## Out of scope

- Videos / storage / worker (Phase 03)
- Channel/video management, public channel page (Phase 04+)

## Conventions to match

- Clean Architecture: pure `domain` entities + ports; `application` use cases; `infrastructure` JPA/security/mail adapters; `web` controllers/DTOs/advice.
- Services throw domain exceptions; the web `@RestControllerAdvice` maps them to HTTP + shared error envelope (never throw HTTP types from use cases).
- Docker service-name hosts (`db`, `mailpit`).
