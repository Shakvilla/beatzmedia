-- WU-EXT-5: record the selected email transport on notification delivery attempts.
-- Existing notification attempts were sent via the SMTP/Mailpit transport.
ALTER TABLE delivery_attempt
    ADD COLUMN provider_code VARCHAR(24) NOT NULL DEFAULT 'smtp';
ALTER TABLE delivery_attempt
    ADD CONSTRAINT delivery_attempt_provider_code_check
    CHECK (provider_code IN ('smtp', 'resend', 'capture', 'frog', 'disabled', 'unknown'));
