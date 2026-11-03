-- WU-ENG-1: authenticated-only durable engagement history.
-- Account/content ids are opaque cross-module refs; only engagement-owned FKs are declared.

CREATE TABLE listening_preference (
    account_id         TEXT        PRIMARY KEY,
    history_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    history_cleared_at TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL
);

CREATE TABLE listening_session (
    id                 TEXT        PRIMARY KEY,
    account_id         TEXT        NOT NULL,
    started_at         TIMESTAMPTZ NOT NULL,
    last_checkpoint_at TIMESTAMPTZ,
    max_sequence       BIGINT      NOT NULL DEFAULT 0 CHECK (max_sequence >= 0),
    closed_at           TIMESTAMPTZ
);

CREATE INDEX idx_listening_session_account_started
    ON listening_session (account_id, started_at DESC, id DESC);

CREATE TABLE listening_checkpoint (
    id                     TEXT        PRIMARY KEY,
    session_id             TEXT        NOT NULL REFERENCES listening_session(id) ON DELETE CASCADE,
    account_id             TEXT        NOT NULL,
    sequence               BIGINT      NOT NULL CHECK (sequence > 0),
    content_kind           TEXT        NOT NULL CHECK (content_kind IN ('track', 'podcast-episode')),
    content_id             TEXT        NOT NULL,
    event_type             TEXT        NOT NULL CHECK (event_type IN (
        'start', 'qualified-play', 'progress', 'completion', 'skip', 'repeat'
    )),
    position_seconds       INTEGER     NOT NULL CHECK (position_seconds >= 0),
    duration_seconds       INTEGER     NOT NULL CHECK (duration_seconds > 0),
    listened_delta_seconds INTEGER     NOT NULL DEFAULT 0 CHECK (listened_delta_seconds >= 0),
    effective              BOOLEAN     NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL,
    aggregated_at          TIMESTAMPTZ,
    CONSTRAINT uq_listening_checkpoint_session_sequence UNIQUE (session_id, sequence)
);

CREATE INDEX idx_listening_checkpoint_account_received
    ON listening_checkpoint (account_id, received_at DESC, id DESC);

CREATE INDEX idx_listening_checkpoint_pending_projection
    ON listening_checkpoint (received_at, id)
    WHERE effective AND aggregated_at IS NULL;

CREATE TABLE listening_progress (
    account_id         TEXT        NOT NULL,
    content_kind       TEXT        NOT NULL CHECK (content_kind IN ('track', 'podcast-episode')),
    content_id         TEXT        NOT NULL,
    position_seconds   INTEGER     NOT NULL CHECK (position_seconds >= 0),
    duration_seconds   INTEGER     NOT NULL CHECK (duration_seconds > 0),
    last_event_type    TEXT        NOT NULL CHECK (last_event_type IN (
        'start', 'qualified-play', 'progress', 'completion', 'skip', 'repeat'
    )),
    completed          BOOLEAN     NOT NULL,
    last_session_id    TEXT        NOT NULL REFERENCES listening_session(id) ON DELETE CASCADE,
    last_sequence      BIGINT      NOT NULL CHECK (last_sequence > 0),
    last_checkpoint_id TEXT        NOT NULL,
    last_activity_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, content_kind, content_id)
);

CREATE INDEX idx_listening_progress_account_activity
    ON listening_progress (account_id, last_activity_at DESC, content_kind, content_id);

CREATE INDEX idx_listening_progress_continue_podcast
    ON listening_progress (account_id, last_activity_at DESC, content_id)
    WHERE content_kind = 'podcast-episode' AND NOT completed;

CREATE TABLE engagement_daily_rollup (
    day                  DATE   NOT NULL,
    content_kind         TEXT   NOT NULL CHECK (content_kind IN ('track', 'podcast-episode')),
    content_id           TEXT   NOT NULL,
    starts               BIGINT NOT NULL DEFAULT 0 CHECK (starts >= 0),
    qualified_plays      BIGINT NOT NULL DEFAULT 0 CHECK (qualified_plays >= 0),
    completions          BIGINT NOT NULL DEFAULT 0 CHECK (completions >= 0),
    skips                BIGINT NOT NULL DEFAULT 0 CHECK (skips >= 0),
    repeats              BIGINT NOT NULL DEFAULT 0 CHECK (repeats >= 0),
    listened_seconds     BIGINT NOT NULL DEFAULT 0 CHECK (listened_seconds >= 0),
    PRIMARY KEY (day, content_kind, content_id)
);
