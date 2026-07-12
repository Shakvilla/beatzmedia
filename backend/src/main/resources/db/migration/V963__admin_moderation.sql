-- V963__admin_moderation.sql
-- WU-ADM-3: admin module — moderation queue (LLFR-ADMIN-04.1), backing store for both the
-- moderation queue itself AND catalog-moderation's `flag` action (LLFR-ADMIN-03.2), which creates
-- a ModerationCase targeting the flagged release. Admin band V9xx per data-and-migrations §4.1.
-- No cross-module FK: target_ref is an opaque id into another module (e.g. "release:<releaseId>"
-- for a catalog-item flag), resolved via reader ports at read time.
--
-- Uses TEXT ids (not native UUID) and "<table>_<col>_chk"/"<table>_<col>_idx" naming — matches the
-- established as-built convention already set by V950 (support_ticket, WU-ADM-7) rather than admin
-- ADD §7's illustrative UUID-PK sketch.

CREATE TABLE moderation_case (
    id           TEXT        PRIMARY KEY,
    target_ref   TEXT        NOT NULL,
    reporter     TEXT        NOT NULL,
    reason       TEXT        NOT NULL
                 CONSTRAINT moderation_case_reason_chk
                 CHECK (reason IN ('Copyright', 'Hate speech', 'Sexual content', 'Spam', 'Impersonation')),
    severity     TEXT        NOT NULL
                 CONSTRAINT moderation_case_severity_chk
                 CHECK (severity IN ('high', 'med', 'low')),
    status       TEXT        NOT NULL DEFAULT 'open'
                 CONSTRAINT moderation_case_status_chk
                 CHECK (status IN ('open', 'in_review', 'resolved')),
    sla_due_at   TIMESTAMPTZ,
    is_escalated BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX moderation_case_status_reason_idx ON moderation_case (status, reason);
CREATE INDEX moderation_case_sla_idx           ON moderation_case (sla_due_at);
CREATE INDEX moderation_case_target_ref_idx    ON moderation_case (target_ref);
