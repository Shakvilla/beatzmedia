-- WU-LIB-1 Library module: likes, follows, saved sets.
-- Composite PKs enforce single-membership (INV-LIB-1).
-- ON CONFLICT DO NOTHING in application layer enforces idempotent PUT.

CREATE TABLE liked_track (
    account_id  UUID        NOT NULL,
    track_id    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, track_id)
);
CREATE INDEX idx_liked_track_account ON liked_track (account_id, created_at DESC);

CREATE TABLE followed_artist (
    account_id  UUID        NOT NULL,
    artist_id   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, artist_id)
);
CREATE INDEX idx_followed_artist_account ON followed_artist (account_id, created_at DESC);

CREATE TABLE followed_playlist (
    account_id  UUID        NOT NULL,
    playlist_id TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, playlist_id)
);
CREATE INDEX idx_followed_playlist_account ON followed_playlist (account_id, created_at DESC);

CREATE TABLE followed_show (
    account_id  UUID        NOT NULL,
    show_id     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, show_id)
);
CREATE INDEX idx_followed_show_account ON followed_show (account_id, created_at DESC);

CREATE TABLE saved_album (
    account_id  UUID        NOT NULL,
    album_id    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, album_id)
);
CREATE INDEX idx_saved_album_account ON saved_album (account_id, created_at DESC);
