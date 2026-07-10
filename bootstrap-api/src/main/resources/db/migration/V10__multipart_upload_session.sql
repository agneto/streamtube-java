-- Phase 08: multipart upload session — one active session per video, held on the video row
-- itself (its lifecycle is the video's own PENDING_UPLOAD window). All null when no session.

ALTER TABLE videos
    ADD COLUMN upload_id         varchar(200),
    ADD COLUMN upload_size_bytes bigint,
    ADD COLUMN upload_part_size  bigint;
