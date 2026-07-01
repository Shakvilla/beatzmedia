-- V204__identity_social_and_password_reset.sql
-- WU-IDN-2: social_identity + password_reset_token tables.
-- Identity ADD §7 / data-and-migrations §4.1 (identity band V2xx; next free after V201-V203).

CREATE TABLE social_identity (
    id           TEXT PRIMARY KEY,
    account_id   TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    provider     TEXT NOT NULL
                 CONSTRAINT social_identity_provider_chk
                 CHECK (provider IN ('facebook','google','twitter')),
    provider_uid TEXT NOT NULL,
    CONSTRAINT social_identity_provider_uk UNIQUE (provider, provider_uid)
);

CREATE INDEX social_identity_account_idx ON social_identity (account_id);

-- Primary key is the SHA-256 hash of the opaque plaintext reset token; the plaintext is never
-- persisted (identity ADD §9 / security note).
CREATE TABLE password_reset_token (
    token_hash TEXT PRIMARY KEY,
    account_id TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX password_reset_token_account_idx ON password_reset_token (account_id);
