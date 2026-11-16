-- Opaque browser sessions used to renew short-lived access JWTs.
-- Only a SHA-256 hash of the random cookie secret is stored.
CREATE TABLE auth_session (
    id           TEXT PRIMARY KEY,
    account_id   TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    token_hash   CHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_auth_session_account_active ON auth_session (account_id, expires_at)
    WHERE revoked_at IS NULL;
