---
kind: phase
name: phase-04-management
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Migration V6 must backfill published_at = updated_at for READY videos, or every video watchable today becomes an invisible draft the moment visibility enforcement lands. Assert the backfill in an E2E against real Postgres."
  - id: ADV-02
    text: "Publication is orthogonal to the processing lifecycle: published_at (null = draft) + visibility enum, no new states in VideoStatus. publish() requires READY — publishing a draft that later fails processing is impossible by construction."
  - id: ADV-03
    text: "UNLISTED means reachable by slug but absent from every listing; drafts must 404 for non-owners on info/stream/download (no existence leak). Cover the whole matrix in E2E."
  - id: ADV-04
    text: "Custom thumbnail reuses the presigned-PUT pattern with image/* content-type and size limit signed into the URL (StoragePort unchanged); the complete step HEAD-checks before swapping thumbnail_key."
  - id: ADV-05
    text: "First paginated endpoints: keep Spring's Pageable out of the application layer — ports take explicit page/size and return a small PageResult, mapped to the envelope in the web layer."
  - id: ADV-06
    text: "Nickname change: uniqueness races are backstopped by the DB constraint translated to 409 (same pattern as users.email). No redirect for old channel URLs — accepted trade-off, document in the public-page endpoint."
  - id: ADV-07
    text: "categories.category_id on videos stays a plain UUID column (no JPA association), keeping the worker persistence unit minimal (Phase 03 lesson)."
---

# Phase 04 — Validation

## Decisions coverage

Reuses existing decisions: storage TD-09 (presigned thumbnail), migrations TD-05 (V6),
errors TD-14 (DomainErrorType categories), tests TD-15 (unit + Testcontainers E2E).
New conventions introduced and recorded in context.md: publication model, visibility
semantics, pagination envelope. No undecided topic blocks implementation.

## Dependency gaps

None. Depends on Phase 02 (auth/channels) and Phase 03 (upload pipeline), both released in
v0.2.0. The improvement-report hardening (after-commit side effects, /api/v1, jsonb) is merged
and assumed as the base.

## Verdict

**clean** — ready to implement.
