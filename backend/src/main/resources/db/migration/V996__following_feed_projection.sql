CREATE TABLE following_feed_item (
    id            TEXT PRIMARY KEY,
    kind          TEXT NOT NULL CHECK (kind IN ('release', 'podcast_episode')),
    content_id    TEXT NOT NULL,
    creator_kind  TEXT NOT NULL CHECK (creator_kind IN ('artist', 'show')),
    creator_id    TEXT NOT NULL,
    title         TEXT NOT NULL,
    subtitle      TEXT NOT NULL,
    image         TEXT,
    route         TEXT NOT NULL,
    published_at  TIMESTAMPTZ NOT NULL,
    projected_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (kind, content_id)
);

CREATE INDEX idx_following_feed_artist
    ON following_feed_item (creator_id, published_at DESC, id)
    WHERE creator_kind = 'artist';

CREATE INDEX idx_following_feed_show
    ON following_feed_item (creator_id, published_at DESC, id)
    WHERE creator_kind = 'show';
