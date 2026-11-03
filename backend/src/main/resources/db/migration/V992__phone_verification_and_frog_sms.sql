-- Phone verification is distinct from artist/account admin verification.
ALTER TABLE fan_settings
    ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE phone_verification_challenge (
    id            TEXT PRIMARY KEY,
    account_id    TEXT NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    phone         TEXT NOT NULL,
    code_hash     TEXT NOT NULL,
    provider_code TEXT NOT NULL CHECK (provider_code IN ('capture', 'frog', 'disabled')),
    status        TEXT NOT NULL CHECK (status IN ('pending', 'sent', 'consumed', 'failed', 'expired')),
    attempts      SMALLINT NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    sent_at       TIMESTAMPTZ,
    consumed_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_phone_verification_active_account
    ON phone_verification_challenge (account_id)
    WHERE status IN ('pending', 'sent');

CREATE INDEX ix_phone_verification_account_created
    ON phone_verification_challenge (account_id, created_at DESC);

CREATE INDEX ix_phone_verification_destination_created
    ON phone_verification_challenge (phone, created_at DESC);
