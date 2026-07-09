-- Phase 05: view counting and same-category suggestions.

-- Monotonic playback counter, changed only via atomic "SET views_count = views_count + 1".
-- No backfill: every existing video legitimately starts at 0 views.
ALTER TABLE videos
    ADD COLUMN views_count bigint NOT NULL DEFAULT 0;

-- Related-videos query: same category, publicly listed, newest publication first. Partial index
-- over exactly the rows that query reads (the channel listing index does not cover it).
CREATE INDEX idx_videos_category_published
    ON videos (category_id, published_at DESC)
    WHERE visibility = 'PUBLIC' AND published_at IS NOT NULL;
