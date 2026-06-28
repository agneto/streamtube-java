-- Phase 02: refresh tokens (rotation/reuse detection) and verification tokens.

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family     uuid         NOT NULL,
    jti        uuid         NOT NULL UNIQUE,
    token_hash varchar(255) NOT NULL UNIQUE,
    expires_at timestamptz  NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family);

CREATE TABLE verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        varchar(32)  NOT NULL,
    token_hash  varchar(255) NOT NULL,
    expires_at  timestamptz  NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_verification_tokens_hash ON verification_tokens (token_hash);
