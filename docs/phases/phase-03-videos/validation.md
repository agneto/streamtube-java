---
kind: phase
name: phase-03-videos
status: clean
issue_count: 0
issues: []
advisories:
  - id: ADV-01
    text: "Two presigners (public/internal) — client-facing URLs signed against STORAGE_PUBLIC_URL, server/worker URLs against STORAGE_ENDPOINT. Verified by a MinIO Testcontainers integration test that performs a real presigned PUT/GET over HTTP (Java equivalent of the NestJS SignatureDoesNotMatch lesson)."
  - id: ADV-02
    text: "VideoEntity.channel_id is a plain UUID column (DB FK only, no JPA association), so the worker persistence unit needs only VideoEntity. Avoids the worker boot failure seen in NestJS (entity metadata for relation not found)."
  - id: ADV-03
    text: "Dockerfile.worker must install ffmpeg (includes ffprobe). The worker reads the video via an internal presigned URL (HTTP input to ffprobe/ffmpeg)."
  - id: ADV-04
    text: "RabbitMQ retry/DLQ: queue declared with x-dead-letter-exchange; listener retries 3x then routes to video.processing.dlq; terminal failures set the video row to ERROR. Worker integration uses RabbitMQ Testcontainers (random port) to avoid host port conflicts."
---

# Phase 03 — Validation

## Decisions coverage

Storage TD-09; queue TD-06; worker TD-10; ffmpeg TD-04; migrations TD-05;
errors TD-14; tests TD-15. All decided.

## Dependency gaps

None. Phase 03 depends on Phase 01 (base/infra) and Phase 02 (channels/auth),
both merged into `dev`.

## Verdict

**clean** — ready to implement.
