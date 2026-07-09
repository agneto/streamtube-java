---
kind: phase
name: phase-06-social
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Every counter changes only via atomic ± 1 SQL in the SAME transaction as the source-row change (reaction upsert, comment insert/delete, subscription insert/delete). The unique constraints are what make double-counting impossible — never increment before knowing the row was actually inserted (ON CONFLICT DO NOTHING returns 0 rows)."
  - id: ADV-02
    text: "Reaction switch (LIKE→DISLIKE) must adjust BOTH counters in one transaction: -1 on the old type, +1 on the new. A naive upsert that only bumps the new counter drifts immediately."
  - id: ADV-03
    text: "Deleting a top-level comment cascades its replies in the database, but videos.comments_count must drop by 1 + number of replies — compute the reply count in the same transaction before/atomically with the delete."
  - id: ADV-04
    text: "Single-level nesting is a use-case rule, not a schema rule: parent_id references comments, so a reply-to-reply is structurally possible. Validate parent.parentId == null AND parent.videoId == videoId → 400 INVALID_PARENT_COMMENT."
  - id: ADV-05
    text: "Interactions re-apply the Phase 04 read rule through VideoViewAccess (draft → 404 for non-owners) plus a published-only write rule (owner on own draft → 409 VIDEO_NOT_PUBLISHED). Do not let authenticated users react to drafts they can't even see."
  - id: ADV-06
    text: "Video and Channel constructors grow counter fields: fix PersistenceMapper, entities and every test fixture in SI-06.1/06.3, not as an afterthought (same ripple as Phase 05's viewsCount)."
  - id: ADV-07
    text: "New JPA entities are API-only. The worker's persistence unit scans a restricted package list — keep reaction/comment/subscription entities out of it, or the worker drags in tables it never touches."
  - id: ADV-08
    text: "SecurityConfig: the only new permitAll is GET /api/v1/comments/**. PUT/DELETE on /channels/{nickname}/subscription must stay authenticated — verify rule order against the existing GET /api/v1/channels/** permitAll (which is GET-only, so the default covers writes)."
  - id: ADV-09
    text: "Counters on JPA entities follow the Phase 05 pattern: updatable = false, so a stale save() (e.g. video PATCH) can never erase concurrent likes/comments/subscribers."
---

# Phase 06 — Validation

## Decisions coverage

Reuses existing decisions: pagination envelope (Phase 04), visibility matrix (Phase 04), atomic
counters with normalized source of truth (Phase 05, extended in context.md), migrations TD-05
(V8), tests TD-15. New conventions recorded in context.md: one reaction per user per target,
single-level replies, comments_count includes replies, subscription idempotency, feed = published
+ PUBLIC of subscribed channels. No undecided topic blocks implementation.

## Dependency gaps

None. Depends on Phase 02 (users/auth), Phase 04 (publication/visibility, channel pages) and
Phase 05 (counter pattern), all merged and released.

## Verdict

**clean** — ready to implement.
