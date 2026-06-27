-- Phase 01 baseline migration.
-- No business tables yet (those arrive in Phase 02: users + channels).
-- Enable pgcrypto so later phases can use gen_random_uuid() for UUID primary keys.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
