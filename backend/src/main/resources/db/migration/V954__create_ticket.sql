-- V954__create_ticket.sql
-- Events module (WU-EVT-1): minted tickets. Events ADD §7.
-- order_id is a bare TEXT reference to a commerce order — deliberately NOT a foreign key
-- (no cross-module FK, conventions §6 / Events ADD §5.2). Minted ONLY on settlement (INV-EVT-3).

CREATE TABLE ticket (
  id                TEXT        PRIMARY KEY,
  event_id          TEXT        NOT NULL REFERENCES event (id),
  tier_id           TEXT        NOT NULL REFERENCES ticket_tier (id),
  order_id          TEXT        NOT NULL,                 -- commerce order ref (no cross-module FK)
  holder_account_id TEXT        NOT NULL,
  holder_name       TEXT        NOT NULL,
  qr_ref            TEXT        NOT NULL UNIQUE,          -- scan/admit reference
  issued_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (order_id, tier_id, qr_ref)
);
CREATE INDEX idx_ticket_holder ON ticket (holder_account_id);
CREATE INDEX idx_ticket_order  ON ticket (order_id);
CREATE INDEX idx_ticket_event  ON ticket (event_id);
