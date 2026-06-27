-- Phase 02: users and their 1:1 channels.

CREATE TABLE users (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email        varchar(255) NOT NULL UNIQUE,
    password     varchar(255) NOT NULL,
    is_confirmed boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE channels (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    name        varchar(50)  NOT NULL,
    nickname    varchar(50)  NOT NULL UNIQUE,
    description text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);
