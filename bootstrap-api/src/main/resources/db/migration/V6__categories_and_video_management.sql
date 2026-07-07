-- Phase 04: categories, video publication/visibility and editing fields.

CREATE TABLE categories (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       varchar(60) NOT NULL UNIQUE,
    slug       varchar(60) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO categories (name, slug) VALUES
    ('Education',     'education'),
    ('Music',         'music'),
    ('Gaming',        'gaming'),
    ('Sports',        'sports'),
    ('Technology',    'technology'),
    ('Entertainment', 'entertainment'),
    ('News',          'news'),
    ('Other',         'other');

ALTER TABLE videos
    ADD COLUMN description  text,
    ADD COLUMN category_id  uuid REFERENCES categories (id),
    ADD COLUMN visibility   varchar(10) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN published_at timestamptz;

-- Backfill: videos that are already watchable predate the publication concept — without this,
-- every READY video would become an invisible draft the moment visibility is enforced.
UPDATE videos SET published_at = updated_at WHERE status = 'READY';

-- Owner panel listing (all statuses, newest first) and public channel listing.
CREATE INDEX idx_videos_channel_created ON videos (channel_id, created_at DESC);
CREATE INDEX idx_videos_channel_published ON videos (channel_id, published_at DESC)
    WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL;
