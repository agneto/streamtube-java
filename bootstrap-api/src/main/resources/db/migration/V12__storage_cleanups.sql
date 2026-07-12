-- Phase 11: durable outbox for storage deletions. Rows are enqueued in the same transaction as
-- the change that orphans the objects (video deletion, stale-draft purge) and drained by the
-- worker's sweeper; an entry only leaves the table after the storage confirmed the wipe.

CREATE TABLE storage_cleanups (
    id         uuid PRIMARY KEY,
    prefix     varchar(600) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_storage_cleanups_created ON storage_cleanups (created_at);
