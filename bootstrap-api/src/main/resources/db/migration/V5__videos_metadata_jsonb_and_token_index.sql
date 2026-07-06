-- Improvement report 6.3/6.4:
-- 1) videos.metadata holds the raw ffprobe JSON; as jsonb Postgres validates it on write and
--    future queries can index/reach into it (codec, resolution) without parsing text.
-- 2) verification tokens are always looked up by (token_hash, type); replace the single-column
--    index with a composite one that matches the query.

ALTER TABLE videos
    ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;

DROP INDEX idx_verification_tokens_hash;
CREATE INDEX idx_verification_tokens_hash_type ON verification_tokens (token_hash, type);
