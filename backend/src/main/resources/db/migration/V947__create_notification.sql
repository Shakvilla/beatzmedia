-- V947__create_notification.sql
-- Notifications module (WU-NOT-1): in-app notification feed + read state. Notifications ADD §7.
-- The delivery_attempt table (email/SMS dispatch, WU-NOT-2) is added in a later migration.
--
-- account ids are TEXT (mirrors account.id, V201) — no cross-module FK (hexagonal: notifications
-- references accounts by id only, never a foreign-key join across module boundaries).

CREATE TABLE notification (
    id            TEXT        PRIMARY KEY,
    recipient_id  TEXT        NOT NULL,
    type          TEXT        NOT NULL CHECK (type IN ('sale','tip','follower','payout','release','system')),
    title         TEXT        NOT NULL,
    body          TEXT        NOT NULL,
    to_route      TEXT,
    is_read       BOOLEAN     NOT NULL DEFAULT FALSE,
    dedupe_key    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at       TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient_created ON notification (recipient_id, created_at DESC);
CREATE INDEX idx_notification_recipient_unread  ON notification (recipient_id) WHERE is_read = FALSE;

-- INV-N4: replaying the same event (same dedupe_key) must not create a duplicate row.
CREATE UNIQUE INDEX uq_notification_dedupe ON notification (dedupe_key) WHERE dedupe_key IS NOT NULL;
