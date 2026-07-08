---
kind: phase
name: phase-05-watch
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "views_count must only ever change via an atomic SQL increment behind the repository port. Loading the entity, bumping a field and saving loses updates under concurrent viewers — the entity exposes the count read-only."
  - id: ADV-02
    text: "Only published videos accumulate views: the owner previewing a draft plays the video but must not count. Assert both directions in E2E (published stream increments; owner draft stream does not)."
  - id: ADV-03
    text: "GetStreamUrlUseCase is @Transactional(readOnly = true) today; the increment forces it to stop being read-only (or to delegate to a small write-path collaborator). Do not leave the UPDATE inside a read-only transaction."
  - id: ADV-04
    text: "Related videos re-apply the Phase 04 visibility matrix: suggestions are published+PUBLIC only, and the base video itself 404s for non-owners when draft. Excluding the video itself must be in the query, not post-filtering."
  - id: ADV-05
    text: "The category suggestions query needs its own partial index (category_id, published_at DESC) WHERE visibility='PUBLIC' AND published_at IS NOT NULL — the existing channel index does not cover it."
  - id: ADV-06
    text: "Video constructor grows one field: fix PersistenceMapper, VideoEntity and every test fixture building a Video in the same slice (SI-05.1/05.3), not as an afterthought."
  - id: ADV-07
    text: "No dedup by design (reloads count again) — record the trade-off where views are documented so it is not mistaken for a bug later."
---

# Phase 05 — Validation

## Decisions coverage

Reuses existing decisions: pagination stays as-is (related is a plain limited list, not an
envelope); visibility matrix from Phase 04; migrations TD-05 (V7); tests TD-15 (unit +
Testcontainers E2E). New conventions recorded in context.md: counter column over event table,
what counts as a view, no dedup. No undecided topic blocks implementation.

## Dependency gaps

None. Depends on Phase 03 (stream path) and Phase 04 (publication/visibility, categories,
listing views), both merged and released in v0.3.0.

## Verdict

**clean** — ready to implement.
