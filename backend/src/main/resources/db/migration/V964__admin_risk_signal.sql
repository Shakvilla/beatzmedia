-- V964__admin_risk_signal.sql
-- WU-ADM-6: admin module — trust & safety risk signals (LLFR-ADMIN-07.1). Backing store for the
-- risk board (GET /v1/admin/risk) and its review/clear/ban actions. Admin band V9xx per
-- data-and-migrations §4.1. No cross-module FK: subject_ref is an opaque ref to the flagged subject
-- (for the `ban` action it is treated as the target account ref, resolved via the identity ban
-- port at action time; a non-account subject 404s there).
--
-- Uses TEXT ids and "<table>_<col>_chk"/"<table>_<col>_idx" naming — matches the established
-- as-built convention (V950 support_ticket, V963 moderation_case) rather than admin ADD §7's
-- illustrative UUID-PK sketch. `type` is free-text (signal category, e.g. 'Payment fraud').

CREATE TABLE risk_signal (
    id           TEXT        PRIMARY KEY,
    subject_ref  TEXT        NOT NULL,
    type         TEXT        NOT NULL,
    detail       TEXT,
    level        TEXT        NOT NULL
                 CONSTRAINT risk_signal_level_chk
                 CHECK (level IN ('high', 'med', 'low')),
    status       TEXT        NOT NULL DEFAULT 'open'
                 CONSTRAINT risk_signal_status_chk
                 CHECK (status IN ('open', 'cleared', 'banned')),
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX risk_signal_status_level_idx ON risk_signal (status, level);
