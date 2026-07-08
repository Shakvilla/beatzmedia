-- V953__create_ticket_tier.sql
-- Events module (WU-EVT-1): ticket tiers. Events ADD §7.
-- price_minor is pesewas (INV-11; currency is always GHS for v1, no currency column).
-- sold <= capacity is INV-EVT-1, DB CHECK-backstopped (the last-line guard behind the
-- SELECT ... FOR UPDATE row lock the application enforces on issuance, OQ-11).

CREATE TABLE ticket_tier (
  id          TEXT PRIMARY KEY,
  event_id    TEXT   NOT NULL REFERENCES event (id) ON DELETE CASCADE,
  name        TEXT   NOT NULL,
  price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
  capacity    INT    NOT NULL CHECK (capacity >= 0),
  sold        INT    NOT NULL DEFAULT 0 CHECK (sold >= 0 AND sold <= capacity), -- INV-EVT-1
  perks       JSONB  NOT NULL DEFAULT '[]',
  UNIQUE (event_id, name)
);
CREATE INDEX idx_tier_event ON ticket_tier (event_id);
