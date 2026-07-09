# phase-05-watch — Progress

**Status:** completed
**SIs:** 6/6 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-05.1 | Domain (viewsCount + repository port additions) | done | constructor ripple fixed in the same slice |
| SI-05.2 | Flyway V7 (views_count + category/published index) | done | applied by Testcontainers E2E |
| SI-05.3 | Persistence (atomic increment, related queries) | done | native `UPDATE ... + 1`; `views_count` is `updatable = false` on the entity so a stale `save()` can never erase concurrent views |
| SI-05.4 | Use cases (view counting on stream, related videos) | done | GetStreamUrlUseCaseTest (published counts, draft preview doesn't), GetRelatedVideosUseCaseTest (category vs fallback, limit clamp 1–20, draft 404) |
| SI-05.5 | Web (related endpoint, views in DTOs, Postman) | done | no security change (`GET /api/v1/videos/**` already permitAll) |
| SI-05.6 | Tests + docs + DoD | done | E2E: streamCountsViewsOnlyForPublishedVideos, relatedVideosAreSameCategoryPublishedPublicOnly |

## Notes

- Planned from the reference project plan (`project-plan.md`, Fase 05). The reference phase is
  mostly frontend (player/layout); the backend slice is view counting + same-category suggestions.
  Anonymous access, download, UNLISTED-by-link and description were already delivered in
  Phases 03–04.
- Likes/comments/subscriptions arrive with Phase 06; ranking/search/home with Phase 07.
- `views` is exposed on every video read (info, related, owner panel, public channel listing) —
  the gap announced in the Phase 04 progress notes is closed.
- No dedup by design: a reload counts again (documented in system-design and fluxo-upload so it is
  not mistaken for a bug).
