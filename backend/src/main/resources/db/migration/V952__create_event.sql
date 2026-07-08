-- V952__create_event.sql
-- Events module (WU-EVT-1): live events. Events ADD §7.
-- lineup is a JSON array of supporting-act names; status is NEVER stored (INV-EVT-2, derived at
-- read time from ticket_tier.sold/capacity in V953).

CREATE TABLE event (
  id                 TEXT PRIMARY KEY,
  title              TEXT        NOT NULL,
  artist_name        TEXT        NOT NULL,
  artist_id          TEXT,
  lineup             JSONB       NOT NULL DEFAULT '[]',
  image              TEXT        NOT NULL,
  event_at           TIMESTAMPTZ NOT NULL,
  doors_time         TEXT,
  venue              TEXT        NOT NULL,
  city               TEXT        NOT NULL,
  region             TEXT,
  category           TEXT        NOT NULL,           -- EventCategory
  description        TEXT,
  age_restriction    TEXT,
  popularity         INT         NOT NULL DEFAULT 0,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_event_category CHECK (category IN
    ('Concert','Festival','Club Night','Listening Party','Tour'))
);
CREATE INDEX idx_event_city     ON event (city);
CREATE INDEX idx_event_category ON event (category);
CREATE INDEX idx_event_date     ON event (event_at);
