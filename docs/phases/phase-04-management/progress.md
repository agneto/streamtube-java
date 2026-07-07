# phase-04-management — Progress

**Status:** implemented (single PR)
**SIs:** 8/8 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-04.1 | Domain (publication/visibility, category, channel rules) | done | `VideoTest`, `ChannelTest` |
| SI-04.2 | Flyway V6 (categories + videos columns + backfill) | done | `V6BackfillMigrationTest` (real Postgres) |
| SI-04.3 | Persistence (category, paginated queries, nickname 409) | done | covered via E2E |
| SI-04.4 | Video use cases (details/publish/thumbnail) | done | `UpdateVideoDetailsUseCaseTest`, `PublishVideoUseCaseTest`, `InitiateThumbnailUploadUseCaseTest`, `CompleteThumbnailUploadUseCaseTest` |
| SI-04.5 | Channel use cases (edit, public page, listings) | done | `UpdateChannelInfoUseCaseTest` |
| SI-04.6 | Visibility enforcement on existing reads | done | `GetVideoInfoUseCaseTest` (matrix) |
| SI-04.7 | Web (controllers, page envelope, security, Postman) | done | `VideosE2ETest`, `ChannelE2ETest` |
| SI-04.8 | Tests + DoD | done | `./gradlew build` green |

## Implementation notes

- `RenameVideoUseCase` was absorbed by `UpdateVideoDetailsUseCase` (PATCH `/videos/{id}` now takes
  `{title?, description?, categoryId?, visibility?}`); `UpdateChannelDescriptionUseCase` was
  absorbed by `UpdateChannelInfoUseCase` (`{name?, nickname?, description?}`).
- PATCH semantics everywhere: absent/null field = untouched; blank description = clears. This
  changed the old `PATCH /channels/me` behavior where `description: null` cleared the bio.
- Pagination is framework-free in the domain (`PageResult<T>`); Spring `Pageable` stays inside the
  JPA adapter. Page size is clamped to 100 in the application layer (`PageRequests`).
- Custom thumbnail key is deterministic (`thumbnails/{slug}-custom`): re-uploads overwrite, and the
  key only becomes the video's thumbnail on `/thumbnail/complete` (409 if the object is missing,
  422 if the video is not READY).
- Draft reads return 404 (not 403) for non-owners so drafts never leak existence; published
  UNLISTED videos are reachable by slug but never listed.
- Nickname uniqueness race handled at the adapter with `saveAndFlush` +
  `DataIntegrityViolationException` → `NICKNAME_ALREADY_TAKEN` (409), mirroring users.email.

## Notes

- Planned from the reference project plan (`project-plan.md`, Fase 04) — the reference NestJS
  backend stopped at Phase 03, so there is no backend contract to mirror; endpoints were derived
  from the reference phase's feature list.
- View/like/comment counts in the panel DTO arrive with Phases 05–06.
