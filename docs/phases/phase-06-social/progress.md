# phase-06-social — Progress

**Status:** not started
**SIs:** 0/8 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-06.1 | Domain (Comment, ReactionType, Subscription + ports, counter accessors) | pending | |
| SI-06.2 | Flyway V8 (4 tables + counter columns + indexes) | pending | |
| SI-06.3 | Persistence (entities API-only, upserts, atomic counters) | pending | |
| SI-06.4 | Use cases: video/comment reactions (+ myReaction no info) | pending | |
| SI-06.5 | Use cases: comments (create/reply/list/delete) | pending | |
| SI-06.6 | Use cases: subscriptions (subscribe/list/feed + channel page) | pending | |
| SI-06.7 | Web (controllers, DTOs, security, Postman) | pending | |
| SI-06.8 | Tests + docs + DoD | pending | |

## Notes

- Planned from the reference project plan (`project-plan.md`, Fase 06). "Interface completa" is
  frontend; the backend slice is reactions + comments (single-level replies) + subscriptions with
  subscriber count and feed.
- Counter pattern extends Phase 05: normalized tables are the source of truth, denormalized
  counters move atomically in the same transaction, `updatable = false` on JPA entities.
- Home/search/ranking arrive with Phase 07.
