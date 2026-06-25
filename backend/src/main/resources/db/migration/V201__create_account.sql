-- V201__create_account.sql
-- WU-IDN-1: Identity module — account + credential tables.
-- social_identity deferred to a later WU-IDN-2 migration (V202 or next in band).
-- Identity ADD §7 / data-and-migrations §4.1.

CREATE TABLE account (
    id          TEXT        PRIMARY KEY,
    name        TEXT        NOT NULL,
    email       TEXT        NOT NULL,
    avatar      TEXT,
    is_artist   BOOLEAN     NOT NULL DEFAULT FALSE,
    is_admin    BOOLEAN     NOT NULL DEFAULT FALSE,
    status      TEXT        NOT NULL DEFAULT 'active'
                CONSTRAINT account_status_chk CHECK (status IN ('active','pending','suspended','banned')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT account_email_uk UNIQUE (email)
);

-- Case-insensitive email lookup index (ADD §7)
CREATE INDEX account_email_idx ON account (lower(email));

CREATE TABLE credential (
    account_id    TEXT PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL,
    algo          TEXT NOT NULL DEFAULT 'argon2id'
);
