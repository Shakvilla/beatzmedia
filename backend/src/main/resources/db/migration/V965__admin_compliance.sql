-- V965__admin_compliance.sql
-- WU-ADM-8: admin module — compliance requests (LLFR-ADMIN-09.1). Backing store for the compliance
-- board (GET /v1/admin/compliance) and its start/complete/export/notice actions. Ghana Data
-- Protection Act (DSAR/takedown/tax). Admin band V9xx per data-and-migrations §4.1. No cross-module
-- FK: subject_ref is an opaque ref to the data subject/target.
--
-- Uses TEXT ids and "<table>_<col>_chk"/"<table>_<col>_idx" naming — matches the established
-- as-built convention (V950 support_ticket, V963 moderation_case, V964 risk_signal) rather than
-- admin ADD §7's illustrative UUID-PK sketch.

CREATE TABLE compliance_request (
    id           TEXT        PRIMARY KEY,
    type         TEXT        NOT NULL
                 CONSTRAINT compliance_request_type_chk
                 CHECK (type IN ('DSAR-export', 'DSAR-delete', 'Takedown', 'Tax')),
    subject_ref  TEXT        NOT NULL,
    detail       TEXT,
    due_at       TIMESTAMPTZ,
    status       TEXT        NOT NULL DEFAULT 'new'
                 CONSTRAINT compliance_request_status_chk
                 CHECK (status IN ('new', 'in_progress', 'completed', 'overdue')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX compliance_request_type_status_idx ON compliance_request (type, status);
CREATE INDEX compliance_request_due_idx         ON compliance_request (due_at);
