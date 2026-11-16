CREATE TABLE discovery_collection_version (
    id              TEXT PRIMARY KEY,
    market          TEXT NOT NULL,
    slug            TEXT NOT NULL,
    formula_version TEXT NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (market ~ '^[A-Z]{2}$'),
    CHECK (slug ~ '^[a-z0-9-]+$')
);

CREATE UNIQUE INDEX uq_discovery_collection_active
    ON discovery_collection_version (market, slug) WHERE active;
CREATE INDEX idx_discovery_collection_version_history
    ON discovery_collection_version (market, slug, generated_at DESC);

CREATE TABLE discovery_collection_entry (
    version_id   TEXT NOT NULL REFERENCES discovery_collection_version(id) ON DELETE CASCADE,
    position     INTEGER NOT NULL CHECK (position > 0),
    content_kind TEXT NOT NULL CHECK (content_kind IN ('single', 'album', 'ep', 'podcast')),
    content_id   TEXT NOT NULL,
    title        TEXT NOT NULL,
    subtitle     TEXT,
    image        TEXT,
    route        TEXT NOT NULL,
    score        BIGINT NOT NULL CHECK (score >= 0),
    reason_code  TEXT NOT NULL,
    PRIMARY KEY (version_id, position),
    UNIQUE (version_id, content_kind, content_id)
);
