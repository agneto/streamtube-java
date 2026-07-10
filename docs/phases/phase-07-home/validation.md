---
kind: phase
name: phase-07-home
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "GET /api/v1/videos shares the class-level mapping with POST /api/v1/videos and sits above GET /{slug}: keep the collection GET parameterless in the path so Spring never routes /videos/{slug} into it (they differ by path depth, but add the E2E assertion anyway)."
  - id: ADV-02
    text: "Search must re-apply the listing rule inside the query (published + PUBLIC): matching a draft's title through the join must be impossible, not post-filtered."
  - id: ADV-03
    text: "ILIKE '%q%' without pg_trgm GIN indexes is a sequential scan — V9 ships the indexes with the feature, not later. The % wildcards mean btree indexes never apply."
  - id: ADV-04
    text: "The q parameter is user input inside a LIKE pattern: escape % and _ (or document that they act as wildcards). Bind it as a parameter — never concatenate."
  - id: ADV-05
    text: "Card assembly batches channel lookups (findByIds) — one query per page, same as comment authors. A per-item findById is an N+1 regression."
  - id: ADV-06
    text: "compose.prod.yaml must not inherit dev defaults for secrets: fail fast with ${VAR:?err} syntax so a prod boot without JWT_SECRET aborts instead of running with the dev fallback."
  - id: ADV-07
    text: "No constructor ripple this phase — do not touch Video/Channel entities; both new reads are queries over existing columns."
---

# Phase 07 — Validation

## Decisions coverage

Reuses existing decisions: pagination envelope (Phase 04), listing visibility rule (Phase 04),
partial-index pattern (Phases 04–05), batch lookups (Phase 06), env-driven CORS (Phase 02). New
conventions recorded in context.md: search = trigram contains on title/channel name without
ranking, card view with channel identity, category filter by id, prod compose override. No
undecided topic blocks implementation.

## Dependency gaps

None. Depends on Phases 04–06 (publication model, views counter, categories, channels), all
merged and released in v0.4.0.

## Verdict

**clean** — ready to implement.
