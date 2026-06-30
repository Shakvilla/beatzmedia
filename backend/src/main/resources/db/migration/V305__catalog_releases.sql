-- WU-CAT-3: Studio release lifecycle tables
-- Catalog ADD §5.2 / LLFR-CATALOG-02.1–02.4

CREATE TABLE release (
    id               TEXT PRIMARY KEY,
    idempotency_key  TEXT UNIQUE,
    artist_id        TEXT NOT NULL REFERENCES artist_profile(id),
    title            TEXT NOT NULL,
    type             TEXT NOT NULL CHECK (type IN ('single','ep','album','mixtape')),
    status           TEXT NOT NULL CHECK (status IN ('draft','in_review','scheduled','live','takedown')),
    visibility       TEXT NOT NULL CHECK (visibility IN ('public','scheduled')),
    scheduled_at     TIMESTAMPTZ,
    went_live_at     TIMESTAMPTZ,
    list_price_minor BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_artist_status ON release(artist_id, status);
CREATE INDEX idx_release_due ON release(scheduled_at) WHERE status = 'scheduled';

CREATE TABLE release_track (
    release_id  TEXT NOT NULL REFERENCES release(id) ON DELETE CASCADE,
    track_id    TEXT NOT NULL REFERENCES track(id),
    position    INT  NOT NULL,
    price_minor BIGINT NOT NULL,
    PRIMARY KEY (release_id, position)
);

CREATE TABLE split_entry (
    id           TEXT PRIMARY KEY,
    track_id     TEXT NOT NULL REFERENCES track(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    email        TEXT NOT NULL,
    role         TEXT NOT NULL,
    percent      INT  NOT NULL CHECK (percent BETWEEN 0 AND 100),
    confirmation TEXT NOT NULL CHECK (confirmation IN ('self','confirmed','pending','auto'))
);

CREATE INDEX idx_split_track ON split_entry(track_id);

CREATE TABLE release_draft (
    id         TEXT PRIMARY KEY,
    artist_id  TEXT NOT NULL REFERENCES artist_profile(id),
    draft      JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_release_draft_artist ON release_draft(artist_id);

-- Add FK constraint from track.release_id -> release.id (column already exists from V302)
ALTER TABLE track ADD CONSTRAINT fk_track_release
    FOREIGN KEY (release_id) REFERENCES release(id);
