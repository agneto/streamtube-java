-- Phase 06: social interactions — reactions, comments (single-level replies), subscriptions.
-- The normalized tables below are the source of truth; the counter columns are denormalized and
-- move only via atomic "± 1" statements in the same transaction as the row change.

CREATE TABLE video_reactions (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    video_id   uuid NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    type       varchar(7) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, video_id)
);
CREATE INDEX idx_video_reactions_video ON video_reactions (video_id);

CREATE TABLE comments (
    id             uuid PRIMARY KEY,
    video_id       uuid NOT NULL REFERENCES videos (id) ON DELETE CASCADE,
    user_id        uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id      uuid REFERENCES comments (id) ON DELETE CASCADE,
    content        varchar(2000) NOT NULL,
    likes_count    bigint NOT NULL DEFAULT 0,
    dislikes_count bigint NOT NULL DEFAULT 0,
    replies_count  bigint NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now()
);
-- Top-level listing (parent_id IS NULL, newest first) and replies listing share this index.
CREATE INDEX idx_comments_video_parent ON comments (video_id, parent_id, created_at DESC);
CREATE INDEX idx_comments_parent ON comments (parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE comment_reactions (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    comment_id uuid NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    type       varchar(7) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, comment_id)
);
CREATE INDEX idx_comment_reactions_comment ON comment_reactions (comment_id);

CREATE TABLE subscriptions (
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel_id uuid NOT NULL REFERENCES channels (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, channel_id)
);
CREATE INDEX idx_subscriptions_channel ON subscriptions (channel_id);
CREATE INDEX idx_subscriptions_user_created ON subscriptions (user_id, created_at DESC);

-- Denormalized counters. No backfill: all interactions legitimately start at 0.
ALTER TABLE videos
    ADD COLUMN likes_count    bigint NOT NULL DEFAULT 0,
    ADD COLUMN dislikes_count bigint NOT NULL DEFAULT 0,
    ADD COLUMN comments_count bigint NOT NULL DEFAULT 0;

ALTER TABLE channels
    ADD COLUMN subscribers_count bigint NOT NULL DEFAULT 0;
