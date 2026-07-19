-- Phase 13: in-app notification feed. One referential table — display fields (actor name/avatar,
-- video title/thumbnail, comment text) are resolved by join at read time, not snapshotted here.
-- recipient_user_id is a USER; actor_channel_id is a CHANNEL (the feed shows a channel identity).
-- read_at IS NULL means unread; the same column answers "unread?" and "read when?".
--
-- Every referenced subject FK is ON DELETE CASCADE: when a user, channel, video or comment is
-- removed, the notifications that point at it go with it — Phase 11's DELETE /videos/{id} already
-- cascades a video's notifications away. No orphan sweeper, no outbox.

CREATE TABLE notifications (
    id                uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type              varchar(32) NOT NULL,
    actor_channel_id  uuid REFERENCES channels (id) ON DELETE CASCADE,
    video_id          uuid REFERENCES videos (id) ON DELETE CASCADE,
    comment_id        uuid REFERENCES comments (id) ON DELETE CASCADE,
    read_at           timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);

-- Feed listing: a recipient's rows, newest first.
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_user_id, created_at DESC);

-- Unread count / unread badge: partial index over only the rows that still matter.
CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_user_id)
    WHERE read_at IS NULL;
