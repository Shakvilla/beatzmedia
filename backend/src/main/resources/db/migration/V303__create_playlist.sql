-- V303__create_playlist.sql
-- WU-CAT-1: Catalog read entities — playlist, playlist_track.
-- browse_category is deferred to WU-CAT-2 per WU-CAT-1 scope definition.
-- Catalog ADD §7 / data-and-migrations §4.1 (band V3xx).

CREATE TABLE playlist (
    id             TEXT        PRIMARY KEY,
    title          TEXT        NOT NULL,
    description    TEXT,
    creator        TEXT        NOT NULL,   -- display name; not an FK to account (cross-module)
    creator_avatar TEXT,
    image          TEXT        NOT NULL,
    is_public      BOOLEAN     NOT NULL DEFAULT TRUE,
    followers      BIGINT
);

CREATE TABLE playlist_track (
    playlist_id TEXT NOT NULL REFERENCES playlist(id) ON DELETE CASCADE,
    track_id    TEXT NOT NULL,             -- catalog track id (no cross-module FK)
    position    INT  NOT NULL,
    PRIMARY KEY (playlist_id, position)
);

CREATE INDEX idx_playlist_track_playlist ON playlist_track(playlist_id);
