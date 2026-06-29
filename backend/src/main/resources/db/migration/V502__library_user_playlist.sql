-- WU-LIB-1 Library module: user-created playlists and their ordered track entries.
-- Composite PK (playlist_id, track_id) enforces track uniqueness (INV-LIB-4).
-- Unique index on (playlist_id, position) enforces contiguous ordering.

CREATE TABLE user_playlist (
    id          UUID        PRIMARY KEY,
    account_id  UUID        NOT NULL,
    title       TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 100),
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_playlist_account ON user_playlist (account_id, created_at DESC);

CREATE TABLE user_playlist_track (
    playlist_id UUID        NOT NULL REFERENCES user_playlist (id) ON DELETE CASCADE,
    track_id    TEXT        NOT NULL,
    position    INT         NOT NULL,
    PRIMARY KEY (playlist_id, track_id)
);
CREATE UNIQUE INDEX uq_playlist_track_position ON user_playlist_track (playlist_id, position);
