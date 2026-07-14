# phase-13-notifications — Progress

**Status:** planned
**SIs:** 0/7 completed

| SI | Description | Status | Notes |
|----|-------------|--------|-------|
| SI-13.1 | Domain (NotificationType, Notification, NotificationRepository port) | todo | referential model; recipient=user, actor=channel |
| SI-13.2 | Flyway V13 (notifications + 2 indexes, FK cascades) | todo | partial index WHERE read_at IS NULL (ADV-10) |
| SI-13.3 | Persistence slice `infrastructure.notification` + API scan registration | todo | native INSERT..SELECT fan-out + JPQL projection join; API-only, worker untouched (ADV-06/07) |
| SI-13.4 | Trigger hooks (Subscribe / CreateComment / PublishVideo) | todo | in-transaction (ADV-01); guards: real-insert, self, first-publish+PUBLIC (ADV-02/03/04) |
| SI-13.5 | Read + web (4 use cases, NotificationView presign, controller) | todo | recipient-scoped list/mark (ADV-09); unread-count on partial index |
| SI-13.6 | Tests (unit guards + fan-out + ownership; E2E full scenario + deletion cascade; smoke) | todo | assert Phase 11 video deletion cascades notifications (ADV-08) |
| SI-13.7 | Docs + DoD (system-design, GUIA, Postman, progress) | todo | note deferred VIDEO_READY + real-time push as evolution |

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
