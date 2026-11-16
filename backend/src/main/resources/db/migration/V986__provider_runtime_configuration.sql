-- Runtime provider credentials are stored separately from the provider selection and encrypted
-- by the application with BEATZ_PROVIDER_CONFIG_ENCRYPTION_KEY (AES-256-GCM).
CREATE TABLE provider_runtime_config (
    config_key       VARCHAR(96) PRIMARY KEY,
    encrypted_values TEXT NOT NULL,
    version          BIGINT NOT NULL CHECK (version > 0),
    updated_at       TIMESTAMPTZ NOT NULL,
    updated_by       VARCHAR(128) NOT NULL
);
