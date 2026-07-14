---
kind: phase
name: phase-13-notifications
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "A notification is a row in the SAME database as the trigger — write it INSIDE the triggering transaction, not through AfterCommitExecutor. AfterCommit exists for external systems (SMTP, the queue) that must not see uncommitted state; a notification must share the fate of the comment/subscribe that caused it (roll back together)."
  - id: ADV-02
    text: "Subscribe emits NEW_SUBSCRIBER only when subscribe() actually inserted a row (it returns that boolean). Notifying unconditionally spams the owner on every idempotent re-subscribe — mirror the subscribers-count rule, which already moves only on a real insert."
  - id: ADV-03
    text: "NEW_VIDEO fan-out must fire only on the FIRST publish AND when visibility == PUBLIC. publish() is a domain no-op once published, so capture wasPublished before calling it; without the visibility guard, publishing an UNLISTED/PRIVATE video would notify subscribers of content they cannot list."
  - id: ADV-04
    text: "Suppress self-notifications: no VIDEO_COMMENT when the commenter's channel is the video's channel; no COMMENT_REPLY when the replier is the parent comment's author. (Self-subscribe is already a 400, and a publisher is not subscribed to their own channel, so NEW_VIDEO needs no extra guard.)"
  - id: ADV-05
    text: "recipient_user_id is a USER; actor_channel_id is a CHANNEL. Resolve the actor as the commenter's/subscriber's channel (findByUserId), and the recipient of VIDEO_COMMENT as the video owner's user_id (via the video's channel). Storing a channel id where a user id belongs (or vice-versa) yields a feed that renders wrong and mark-read scoping that silently fails."
  - id: ADV-06
    text: "Fan-out is ONE native INSERT INTO notifications (...) SELECT ... FROM subscriptions WHERE channel_id = :cid — never a loop of per-subscriber inserts. It joins the social subscriptions table, so it stays on the API side (native, like Phase 06's findSubscriptionFeed); the worker's persistence unit never sees it."
  - id: ADV-07
    text: "The notifications slice (infrastructure.notification) is API-ONLY: register it in the API @EntityScan/@EnableJpaRepositories next to infrastructure.social, and keep it out of the worker's scan. This is the very reason VIDEO_READY/FAILED (worker-sourced) is deferred — adding it later means moving the slice to shared persistence, a deliberate follow-up, not a silent drift now."
  - id: ADV-08
    text: "Cleanup is FK ON DELETE CASCADE, nothing else. Confirm the four FKs (users/channels/videos/comments) cascade and that Phase 11's video deletion therefore removes the video's notifications — assert it in E2E. Do NOT add an outbox/sweeper; there is no leak path that cascades don't cover."
  - id: ADV-09
    text: "Read and mark endpoints are strictly recipient-scoped: list filters by recipient_user_id, and markRead uses WHERE id = :id AND recipient_user_id = :uid (returning affected-row count) so one user can never read or flip another's notifications. A bare markRead(id) is an IDOR."
  - id: ADV-10
    text: "unread-count and the default unread view ride the partial index WHERE read_at IS NULL. Keep read state as a nullable read_at timestamp (null = unread), not a boolean, so the same column answers 'unread?' and 'when read?' and backs the partial index."
---

# Phase 13 — Validation

## Decisions coverage

Builds only on surfaces shipped through Phase 06 (subscriptions, comments/replies) and Phase 04/07
(publish, visibility). Every trigger has a defined recipient, actor, guard, and transaction
boundary; the read side is fully specified (four routes, recipient-scoped, partial-index-backed).
New conventions recorded in context.md: the referential notification model, in-transaction creation
(not AfterCommit), single-statement fan-out, the API-only `infrastructure.notification` slice, and
FK-cascade cleanup. No undecided topic blocks implementation.

## Dependency gaps

None. `subscribe()` already returns the created-flag the NEW_SUBSCRIBER guard needs; `publish()` is
already idempotent for the first-publish guard; the native-join precedent (`findSubscriptionFeed`)
already exists for the fan-out; FK cascades already exist on the referenced tables. The only new
schema is `notifications` (V13). The worker is not touched.

## Verdict

**clean** — ready to implement.
