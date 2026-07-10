---
kind: phase
name: phase-08-multipart
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "ETags never reach the client: complete-multipart must build the part list from ListParts server-side. Accepting client-sent ETags would silently reintroduce the CORS ExposeHeaders dependency and a lost-state failure mode."
  - id: ADV-02
    text: "Part URLs sign the exact Content-Length of THAT part (last part = remainder, not partSize). Signing every part with partSize makes the last part unuploadable whenever size % partSize != 0."
  - id: ADV-03
    text: "The final HEAD size check is what enforces the declared sizeBytes — per-part signatures alone don't stop a client from completing with fewer parts than promised. On mismatch, delete the assembled object before failing, or a wrong-size object stays consumable."
  - id: ADV-04
    text: "Complete must publish the job only AFTER commit (AfterCommitExecutor), exactly like complete-upload — extract and reuse the existing tail instead of duplicating the QUEUED+publish logic."
  - id: ADV-05
    text: "CompleteMultipartUpload on S3 requires parts sorted by partNumber and at least one part; ListParts is paginated (1000/page) — iterate to exhaustion or a >8 GB upload completes with missing parts."
  - id: ADV-06
    text: "Video constructor grows 3 fields: PersistenceMapper, VideoEntity and every fixture in the same slice (the Phase 05/06 ripple, again)."
  - id: ADV-07
    text: "Fake storage in E2E must mimic the part lifecycle (upload part → list → complete materializes the object) or the tests validate nothing about resume; keep it a simple in-memory map keyed by (uploadId, partNumber)."
  - id: ADV-08
    text: "Abandoned sessions hold real bytes invisible in the bucket: the lifecycle rule (abort incomplete multipart after N days) goes into deploy.md in THIS phase — shipping the feature without the ops note recreates the orphan problem §3.3 already documents for PENDING_UPLOAD rows."
---

# Phase 08 — Validation

## Decisions coverage

Extends the recorded evolution path (system-design §3.2) without changing any §3.x decision:
bytes stay out of the API, the client still confirms, the record is still born at initiate, the
pipeline after QUEUED is untouched. New conventions in context.md: server-held ETags, server-
dictated part size, session state on the video row, part-URL TTL, lifecycle cleanup. No undecided
topic blocks implementation.

## Dependency gaps

None. Depends on the Phase 03 upload flow and its hardening, all shipped since v0.2.x; no
interaction with Phases 04–07 features.

## Verdict

**clean** — ready to implement.
