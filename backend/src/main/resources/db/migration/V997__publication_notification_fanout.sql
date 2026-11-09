CREATE TABLE publication_notification_fanout (
    id                TEXT PRIMARY KEY,
    publication_kind  TEXT NOT NULL CHECK (publication_kind IN ('release', 'podcast_episode')),
    content_id        TEXT NOT NULL,
    target_kind       TEXT NOT NULL CHECK (target_kind IN ('artist', 'show')),
    target_id         TEXT NOT NULL,
    title             TEXT NOT NULL,
    body              TEXT NOT NULL,
    route             TEXT NOT NULL,
    cursor            TEXT,
    status            TEXT NOT NULL CHECK (status IN ('pending', 'completed')),
    retry_count       INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_attempt_at   TIMESTAMPTZ NOT NULL,
    last_error        TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    UNIQUE (publication_kind, content_id),
    CHECK ((status = 'completed') = (completed_at IS NOT NULL))
);

CREATE INDEX idx_publication_notification_fanout_due
    ON publication_notification_fanout (next_attempt_at, updated_at, id)
    WHERE status = 'pending';
