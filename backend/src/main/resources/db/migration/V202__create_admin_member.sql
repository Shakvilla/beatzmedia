-- V202__create_admin_member.sql
-- WU-IDN-4: Identity module — admin_member table.
-- Identity ADD §7 / data-and-migrations §4.1 (identity band V2xx).

CREATE TABLE admin_member (
    id             TEXT        PRIMARY KEY,
    account_id     TEXT        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    role           TEXT        NOT NULL
                   CONSTRAINT admin_member_role_chk
                   CHECK (role IN ('super-admin','finance','moderator','editor','support')),
    last_active_at TIMESTAMPTZ,
    CONSTRAINT admin_member_account_uk UNIQUE (account_id)
);

CREATE INDEX admin_member_role_idx ON admin_member (role);
