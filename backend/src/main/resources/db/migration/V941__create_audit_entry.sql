-- V941__create_audit_entry.sql
-- WU-IDN-4 (stub) / WU-AUD-1 (full): Audit module — audit_entry table (append-only).
-- INV-10: every privileged mutation appends exactly one AuditEntry.
-- Audit band V9xx per data-and-migrations §4.1.

CREATE TABLE audit_entry (
    id          TEXT        PRIMARY KEY,
    actor_id    TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    target_type TEXT        NOT NULL,
    target_id   TEXT        NOT NULL,
    type        TEXT        NOT NULL
                CONSTRAINT audit_entry_type_chk
                CHECK (type IN ('USER','CATALOG','FINANCE','MODERATION','SETTINGS','EDITORIAL')),
    reason      TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

-- Index for efficient audit-log queries (WU-AUD-1 read endpoint will use these)
CREATE INDEX audit_entry_actor_idx    ON audit_entry (actor_id);
CREATE INDEX audit_entry_occurred_idx ON audit_entry (occurred_at DESC);
CREATE INDEX audit_entry_type_idx     ON audit_entry (type);
