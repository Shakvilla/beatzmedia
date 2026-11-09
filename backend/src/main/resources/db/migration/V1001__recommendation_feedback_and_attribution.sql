CREATE TABLE recommendation_attribution (
    id              TEXT        PRIMARY KEY,
    account_id      TEXT        NOT NULL,
    content_kind    TEXT        NOT NULL CHECK (content_kind IN ('single', 'album', 'ep', 'podcast')),
    content_id      TEXT        NOT NULL,
    source_type     TEXT        NOT NULL CHECK (source_type IN ('artist', 'podcast')),
    source_id       TEXT        NOT NULL,
    surface         TEXT        NOT NULL,
    reason_code     TEXT        NOT NULL,
    ranking_version TEXT        NOT NULL,
    position        INTEGER     NOT NULL CHECK (position > 0),
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_recommendation_attribution_account_expiry
    ON recommendation_attribution (account_id, expires_at DESC);
CREATE INDEX idx_recommendation_attribution_expiry
    ON recommendation_attribution (expires_at, id);

CREATE TABLE recommendation_event (
    event_id          TEXT        PRIMARY KEY,
    recommendation_id TEXT        NOT NULL REFERENCES recommendation_attribution(id) ON DELETE CASCADE,
    event_type        TEXT        NOT NULL CHECK (
        event_type IN ('impression', 'open', 'start', 'qualified-play', 'completion')
    ),
    received_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_recommendation_event_attribution
    ON recommendation_event (recommendation_id, received_at DESC);

CREATE TABLE recommendation_suppression (
    id          TEXT        PRIMARY KEY,
    account_id  TEXT        NOT NULL,
    target_key  TEXT        NOT NULL,
    target_type TEXT        NOT NULL CHECK (target_type IN ('content', 'artist', 'podcast')),
    target_kind TEXT        NOT NULL,
    target_id   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (account_id, target_key)
);

CREATE INDEX idx_recommendation_suppression_account_target
    ON recommendation_suppression (account_id, target_key);
