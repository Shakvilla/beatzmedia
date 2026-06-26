-- V302__create_track_credit_lyrics.sql
-- WU-CAT-1: Catalog read entities — track, track_credit, lyrics, lyric_line.
-- search_tsv column and GIN/pg_trgm indexes are DEFERRED to WU-SRCH-1 (V3xx+) because
-- pg_trgm extension is not yet enabled and would break a clean empty-DB migration.
-- Catalog ADD §7 / data-and-migrations §4.1 (band V3xx).

CREATE TABLE track (
    id           TEXT        PRIMARY KEY,
    title        TEXT        NOT NULL,
    artist_id    TEXT        NOT NULL REFERENCES artist_profile(id),
    artist_name  TEXT        NOT NULL,
    album_id     TEXT        REFERENCES album(id),
    album_title  TEXT,
    release_id   TEXT,                   -- cross-module ref (no FK); used by WU-CAT-3+
    duration_sec INT         NOT NULL,
    image        TEXT        NOT NULL,
    ownership    TEXT        NOT NULL
                 CONSTRAINT track_ownership_chk
                   CHECK (ownership IN ('owned', 'free', 'for-sale')),
    price_minor  BIGINT,                 -- present when ownership='for-sale'; pesewas
    plays        BIGINT      NOT NULL DEFAULT 0,
    audio_url    TEXT,
    quality      TEXT,
    year         INT,
    status       TEXT        NOT NULL DEFAULT 'ready'
                 CONSTRAINT track_status_chk
                   CHECK (status IN ('uploading', 'ready', 'error'))
);

CREATE INDEX idx_track_artist ON track(artist_id);
CREATE INDEX idx_track_album  ON track(album_id);

CREATE TABLE track_credit (
    track_id TEXT     NOT NULL REFERENCES track(id) ON DELETE CASCADE,
    role     TEXT     NOT NULL,
    names    TEXT[]   NOT NULL,
    PRIMARY KEY (track_id, role)
);

CREATE TABLE lyrics (
    track_id TEXT PRIMARY KEY REFERENCES track(id) ON DELETE CASCADE
);

CREATE TABLE lyric_line (
    track_id TEXT NOT NULL REFERENCES lyrics(track_id) ON DELETE CASCADE,
    t_sec    INT  NOT NULL,
    text     TEXT NOT NULL,
    PRIMARY KEY (track_id, t_sec)
);
