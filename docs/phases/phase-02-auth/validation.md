---
kind: phase
name: phase-02-auth
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Refresh-token rotation grace period (10s) and reuse detection are custom domain logic ported 1:1 from the NestJS backend; they must be covered by integration tests (revoke-then-reuse → family revoked + 401; reuse within grace → fresh access token)."
  - id: ADV-02
    text: "forgot-password must always return 204 regardless of whether the email exists, to avoid user enumeration (mirrors NestJS)."
  - id: ADV-03
    text: "Argon2PasswordEncoder requires Bouncy Castle on the classpath; add org.bouncycastle:bcprov-jdk18on to infrastructure."
  - id: ADV-04
    text: "JPA entity scanning: bootstrap-api main must @EntityScan/@EnableJpaRepositories the infrastructure persistence packages (the main app lives in com.streamtube.api, entities/repos in com.streamtube.infrastructure)."
---

# Phase 02 — Validation

## Decisions coverage

All capabilities trace to decided TDs: domain/layering TD-02; persistence TD-04;
migrations TD-05; auth/JWT TD-07; password TD-08; email TD-11; OpenAPI TD-12;
rate limiting TD-13; validation/errors TD-14; testing TD-15.

## Dependency gaps

None. Phase 02 depends only on Phase 01 (base, now merged into `dev`).

## Verdict

**clean** — ready to implement.
