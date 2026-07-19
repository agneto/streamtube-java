# phase-13-notifications — Progress

**Status:** done
**SIs:** 7/7 completed

| SI | Description | Status | Notes |
|----|-------------|--------|-------|
| SI-13.1 | Domain (NotificationType, Notification, NotificationRepository port) | done | referential model; recipient=user, actor=channel; `NotificationFeedRow` carries `readAt` (read flag derived in the view) |
| SI-13.2 | Flyway V13 (notifications + 2 indexes, FK cascades) | done | partial index WHERE read_at IS NULL (ADV-10); all four FKs ON DELETE CASCADE (ADV-08) |
| SI-13.3 | Persistence slice `infrastructure.notification` + API scan registration | done | native INSERT..SELECT fan-out (`gen_random_uuid()`) + JPQL ad-hoc-join projection; registered next to `infrastructure.social`; worker untouched (ADV-06/07) |
| SI-13.4 | Trigger hooks (Subscribe / CreateComment / PublishVideo) | done | in-transaction (ADV-01); guards: real-insert, self-video/self-comment, first-publish+PUBLIC (ADV-02/03/04) |
| SI-13.5 | Read + web (4 use cases, NotificationView presign, controller) | done | recipient-scoped list/mark (ADV-09); unread-count on partial index; thumbnail presigned in `ListNotificationsUseCase` |
| SI-13.6 | Tests (unit guards + fan-out + ownership; E2E full scenario + deletion cascade) | done | 242 tests, 0 failures; E2E asserts the 4 triggers, IDOR no-op, and video-deletion cascade (ADV-08) |
| SI-13.7 | Docs + DoD (system-design, GUIA, Postman, progress) | done | deferred VIDEO_READY + real-time push noted as evolution |

## Notes

- Sixth post-roadmap improvement. Notifications were explicitly outside the reference plan
  (`phase-06-social/context.md`), so this is designed fresh over the Phase 06 social surface.
- Scope is the four **API-side social** events (NEW_SUBSCRIBER, VIDEO_COMMENT, COMMENT_REPLY,
  NEW_VIDEO). Worker-sourced VIDEO_READY/FAILED is deferred on purpose to keep the whole phase
  API-only and the notifications slice out of the worker's persistence unit.
- No scheduler, no outbox, no worker change: notifications are written in the triggering
  transaction and cleaned up by FK cascade (Phase 11's deletion already cascades them).
- Fan-out for NEW_VIDEO is a single native `INSERT..SELECT` over `subscriptions`, mirroring
  Phase 06's `findSubscriptionFeed`.

## Validation (post-implementation)

- `./gradlew spotlessApply build` green — 242 tests, 0 failures (Testcontainers E2E ran against
  postgres:17-alpine; Docker up locally).
- The feed projection uses JPQL ad-hoc `LEFT JOIN`s (Hibernate 6) over ChannelEntity/VideoEntity/
  CommentEntity — validated at API startup, never loaded by the worker.
- The read-side timestamp for `read_at` is set in the adapter (`Instant.now()`); not domain-critical
  and not asserted for an exact value.
