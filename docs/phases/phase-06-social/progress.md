# phase-06-social — Progress

**Status:** completed
**SIs:** 8/8 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-06.1 | Domain (Comment, ReactionType, Subscription + ports, counter accessors) | done | Video/Channel constructor ripple fixed in the same slice |
| SI-06.2 | Flyway V8 (4 tables + counter columns + indexes) | done | applied by Testcontainers E2E |
| SI-06.3 | Persistence (entities API-only, upserts, atomic counters) | done | social slice lives in `infrastructure.social`, registered only in the API bootstrap (worker untouched); counter moves gated by affected-row counts; `updatable = false` on all counters |
| SI-06.4 | Use cases: video/comment reactions (+ myReaction no info) | done | SetVideoReactionUseCaseTest (published-only, draft 404/409) |
| SI-06.5 | Use cases: comments (create/reply/list/delete) | done | CreateCommentUseCaseTest (reply-to-reply 400, cross-video parent 400, draft 409/404, blank 400), DeleteCommentUseCaseTest (author-only) |
| SI-06.6 | Use cases: subscriptions (subscribe/list/feed + channel page) | done | SubscribeUseCaseTest (self 400, unknown 404) |
| SI-06.7 | Web (controllers, DTOs, security, Postman) | done | only security change: `GET /api/v1/comments/**` permitAll; Postman "Social" folder (12 requests) |
| SI-06.8 | Tests + docs + DoD | done | SocialE2ETest: reaction lifecycle with exact counters, comment thread + counter math on delete, comment reactions, subscription flow + feed |

## Notes

- Planned from the reference project plan (`project-plan.md`, Fase 06). "Interface completa" is
  frontend; the backend slice is reactions + comments (single-level replies) + subscriptions with
  subscriber count and feed.
- Counter pattern extends Phase 05: normalized tables are the source of truth, denormalized
  counters move atomically in the same transaction, `updatable = false` on JPA entities.
- Home/search/ranking arrive with Phase 07.
