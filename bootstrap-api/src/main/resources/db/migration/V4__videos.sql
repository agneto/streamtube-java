-- Phase 03: videos belonging to channels, with processing lifecycle.

CREATE TABLE videos (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id       uuid         NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    title            varchar(255) NOT NULL,
    slug             varchar(16)  NOT NULL UNIQUE,
    status           varchar(20)  NOT NULL DEFAULT 'PENDING_UPLOAD',
    storage_key      varchar(500) NOT NULL,
    thumbnail_key    varchar(500),
    duration_seconds double precision,
    metadata         text,
    error_message    text,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_videos_channel_id ON videos (channel_id);
