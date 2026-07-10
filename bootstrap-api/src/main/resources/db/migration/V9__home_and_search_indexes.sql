-- Phase 07: home listing and search. No new tables/columns — indexes only.

-- Search is a case-insensitive "contains" (ILIKE '%q%') on video title and channel name; the
-- leading wildcard makes btree indexes useless, so trigram GIN indexes keep it index-backed.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_videos_title_trgm ON videos USING gin (title gin_trgm_ops);
CREATE INDEX idx_channels_name_trgm ON channels USING gin (name gin_trgm_ops);

-- Global home listing (newest published PUBLIC first), over exactly the rows that query reads.
CREATE INDEX idx_videos_listed_published
    ON videos (published_at DESC)
    WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL;
