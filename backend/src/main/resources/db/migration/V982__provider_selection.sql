CREATE TABLE provider_selection (
    capability    VARCHAR(32)  PRIMARY KEY,
    provider_code VARCHAR(64)  NOT NULL,
    version       BIGINT       NOT NULL CHECK (version > 0),
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(128) NOT NULL,
    CONSTRAINT ck_provider_selection_capability
        CHECK (capability IN ('storage', 'email', 'sms', 'payment')),
    CONSTRAINT ck_provider_selection_code
        CHECK (
            (capability = 'storage' AND provider_code IN ('minio', 'mega_s4')) OR
            (capability = 'email' AND provider_code IN ('smtp', 'resend', 'disabled')) OR
            (capability = 'sms' AND provider_code IN ('capture', 'frog', 'disabled')) OR
            (capability = 'payment' AND provider_code IN ('sandbox', 'redde', 'disabled'))
        )
);

INSERT INTO provider_selection (capability, provider_code, version, updated_at, updated_by)
VALUES
    ('storage', 'minio', 1, now(), 'system:migration'),
    ('email', 'smtp', 1, now(), 'system:migration'),
    ('sms', 'capture', 1, now(), 'system:migration'),
    ('payment', 'disabled', 1, now(), 'system:migration');
