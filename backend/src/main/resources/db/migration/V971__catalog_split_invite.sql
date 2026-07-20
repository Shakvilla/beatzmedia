-- WU-CAT-9: collaborator split invite/accept.
-- split_invite mirrors identity's password_reset_token (hash-only, single-use, time-boxed).
CREATE TABLE split_invite (
    id          TEXT PRIMARY KEY,
    release_id  TEXT NOT NULL REFERENCES release(id) ON DELETE CASCADE,
    email       TEXT NOT NULL,
    token_hash  TEXT NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    outcome     TEXT CHECK (outcome IN ('accepted','declined')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_split_invite_release ON split_invite(release_id);

-- Link a confirmed collaborator split to their account (no cross-module FK); widen states.
ALTER TABLE split_entry ADD COLUMN account_id TEXT;
ALTER TABLE split_entry DROP CONSTRAINT split_entry_confirmation_check;
ALTER TABLE split_entry ADD CONSTRAINT split_entry_confirmation_check
    CHECK (confirmation IN ('self','confirmed','pending','auto','declined'));
