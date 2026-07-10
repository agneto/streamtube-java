# phase-08-multipart — Progress

**Status:** not started
**SIs:** 0/7 completed

| SI | Description | Status | Tests |
|----|-------------|--------|-------|
| SI-08.1 | Domain (upload session fields + rules, exceptions) | pending | |
| SI-08.2 | Flyway V10 (upload_id, upload_size_bytes, upload_part_size) | pending | |
| SI-08.3 | Storage (multipart methods on StoragePort + S3 adapter) | pending | |
| SI-08.4 | Use cases (initiate, issue part URLs, list parts, complete, abort) | pending | |
| SI-08.5 | Web (routes, DTOs, Postman) | pending | |
| SI-08.6 | Tests (unit + E2E fake parts + smoke with resume) | pending | |
| SI-08.7 | Docs + DoD | pending | |

## Notes

- First post-roadmap improvement phase (reference plan ended at 07): implements the evolution
  recorded in `system-design.md` §3.2 — multipart upload with per-part retry and resume for bad
  connections.
- Single-PUT flow untouched; nothing after `QUEUED` changes.
- Key design choice: the client never sees ETags — completion is assembled server-side from
  `ListParts`, which is also what makes resume trivial.
