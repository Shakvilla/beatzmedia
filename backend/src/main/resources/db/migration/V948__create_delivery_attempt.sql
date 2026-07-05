-- V948__create_delivery_attempt.sql
-- Notifications module (WU-NOT-2): email/SMS dispatch attempts + retry/backoff state machine.
-- Notifications ADD §7 / §8.
--
-- ids are TEXT (mirrors notification.id, V947) — no cross-module FK beyond notification itself.

CREATE TABLE delivery_attempt (
    id                        TEXT        PRIMARY KEY,
    notification_id           TEXT        NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    channel                    TEXT        NOT NULL CHECK (channel IN ('email','sms')),
    provider_idempotency_key   TEXT        NOT NULL,
    status                     TEXT        NOT NULL CHECK (status IN ('pending','sent','failed','dead')),
    retry_count                INT         NOT NULL DEFAULT 0,
    last_error                 TEXT,
    next_attempt_at            TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_attempt_notification ON delivery_attempt (notification_id);

-- Backs the WU-PLT-2 scheduled retry sweep: due rows are pending/failed with next_attempt_at due.
CREATE INDEX idx_delivery_attempt_due ON delivery_attempt (next_attempt_at)
    WHERE status IN ('pending','failed');

-- Send-idempotency (INV-N4/N5 companion): a channel is dispatched at most once per notification.
CREATE UNIQUE INDEX uq_delivery_attempt_channel ON delivery_attempt (notification_id, channel);
