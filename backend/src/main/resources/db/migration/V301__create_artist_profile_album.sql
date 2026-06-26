-- V301__create_artist_profile_album.sql
-- WU-CAT-1: Catalog read entities — artist_profile, artist_show, album.
-- Catalog ADD §7 / data-and-migrations §4.1 (band V3xx).
-- NOTE: catalog.md §7 lists illustrative V10–V13 numbers; this PR supersedes those
-- with the correct V301–V303 band numbers per the global migration registry.

CREATE TABLE artist_profile (
    id                TEXT        PRIMARY KEY,
    name              TEXT        NOT NULL,
    image             TEXT        NOT NULL,
    cover_image       TEXT,
    verified          BOOLEAN     NOT NULL DEFAULT FALSE,
    monthly_listeners BIGINT,
    followers         BIGINT,
    bio               TEXT,
    location          TEXT,
    genres            TEXT[]      NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Upcoming shows for an artist page (LLFR-CATALOG-01.4 / Show VO).
-- Stored in a separate table so we can add/remove individual shows.
CREATE TABLE artist_show (
    id        TEXT    PRIMARY KEY,
    artist_id TEXT    NOT NULL REFERENCES artist_profile(id) ON DELETE CASCADE,
    date      TEXT    NOT NULL,  -- ISO date string, e.g. "2026-05-22"
    city      TEXT    NOT NULL,
    venue     TEXT    NOT NULL,
    position  INT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_artist_show_artist ON artist_show(artist_id);

CREATE TABLE album (
    id               TEXT        PRIMARY KEY,
    title            TEXT        NOT NULL,
    artist_id        TEXT        NOT NULL REFERENCES artist_profile(id),
    artist_name      TEXT        NOT NULL,
    year             INT         NOT NULL,
    cover_image      TEXT        NOT NULL,
    genres           TEXT[]      NOT NULL DEFAULT '{}',
    -- INV-5: list_price_minor = round(Σ track.price_minor × (1 − bundleDiscountPct/100))
    -- Recomputed by the application layer on release submit/edit (WU-CAT-3+).
    list_price_minor BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_album_artist ON album(artist_id);
