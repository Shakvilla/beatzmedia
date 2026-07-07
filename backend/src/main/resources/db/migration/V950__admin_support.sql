-- V950__admin_support.sql
-- WU-ADM-7: admin module — support tickets + message thread (LLFR-ADMIN-08.1).
-- Admin band V9xx per data-and-migrations §4.1. No cross-module FKs: requester_ref is an opaque
-- id into the identity module's account table, resolved via the IdentityReader port at read time.

CREATE TABLE support_ticket (
    id            TEXT        PRIMARY KEY,
    subject       TEXT        NOT NULL,
    requester_ref TEXT        NOT NULL,
    channel       TEXT        NOT NULL,
    priority      TEXT        NOT NULL DEFAULT 'normal'
                  CONSTRAINT support_ticket_priority_chk
                  CHECK (priority IN ('high', 'normal', 'low')),
    status        TEXT        NOT NULL DEFAULT 'open'
                  CONSTRAINT support_ticket_status_chk
                  CHECK (status IN ('open', 'pending', 'resolved')),
    assignee_id   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX support_ticket_status_idx      ON support_ticket (status, priority);
CREATE INDEX support_ticket_requester_idx   ON support_ticket (requester_ref);
CREATE INDEX support_ticket_created_at_idx  ON support_ticket (created_at DESC);

CREATE TABLE support_message (
    id          TEXT        PRIMARY KEY,
    ticket_id   TEXT        NOT NULL REFERENCES support_ticket (id) ON DELETE CASCADE,
    from_party  TEXT        NOT NULL
                CONSTRAINT support_message_from_party_chk
                CHECK (from_party IN ('user', 'agent')),
    author      TEXT        NOT NULL,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX support_message_ticket_idx ON support_message (ticket_id, created_at);
