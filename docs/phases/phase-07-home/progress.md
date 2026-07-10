# phase-07-home — Progress

**Status:** completed
**SIs:** 7/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-07.1 | Domain (listing/search port methods, InvalidSearchQueryException) | done | no entity changes, as planned (ADV-07 held) |
| SI-07.2 | Flyway V9 (pg_trgm + trigram GIN + partial home index) | done | applied by Testcontainers E2E |
| SI-07.3 | Persistence (global listing + title/channel search queries) | done | search re-applies the listing rule in-query; `%`/`_`/`!` escaped in the adapter (`escape '!'`) |
| SI-07.4 | Use cases (home grid, search, VideoCardView batch assembly) | done | SearchVideosUseCaseTest (trim + min 2 chars), ListHomeVideosUseCaseTest (category pass-through, one batch channel lookup) |
| SI-07.5 | Web (GET /videos, SearchController, security, Postman) | done | `GET /api/v1/search` permitAll; Postman "Home & Busca" (4 requests) |
| SI-07.6 | Prod (compose.prod.yaml + docs/deploy.md) | done | fail-fast validated: `config` aborts without secrets; only 8080/9000 published; mailpit off via profile |
| SI-07.7 | Tests + docs + DoD | done | E2E: homeGridListsOnlyPublishedPublicNewestFirst, searchMatchesTitleOrChannelNameOfListedVideosOnly (incl. `%` literal) |

## Notes

- Planned from the reference project plan (`project-plan.md`, Fase 07 — final phase). Frontend
  items (navbar, infinite scroll, responsive) are out; pagination already exists.
- First phase with **no entity/constructor changes** — both features are pure reads over
  existing columns.
- Closing this phase completes the reference roadmap on the backend side.
