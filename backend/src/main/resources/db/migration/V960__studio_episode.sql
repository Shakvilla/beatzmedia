-- V960__studio_episode.sql
-- WU-STU-2: studio_episode — podcast episode lifecycle (draft/scheduled/published, INV-7),
-- premium/early-access (premium ⇒ price_minor > 0, chk_premium_price backstop), idempotent
-- create (idempotency_key + request_hash). Studio ADD §7.
--
-- id/show_id/artist_id are TEXT (not native UUID), matching the codebase-wide convention for
-- primary keys populated by the platform IdGenerator (UUIDv7-as-string) — same deviation as
-- studio_profile.artist_id (V958) / studio_podcast_show (V959). show_id FKs to
-- studio_podcast_show (same module, no cross-module FK violation); artist_id has no FK (matched
-- by convention only, mirroring studio_podcast_show.artist_id).

CREATE TABLE studio_episode (
    id                TEXT PRIMARY KEY,
    show_id           TEXT NOT NULL REFERENCES studio_podcast_show (id),
    artist_id         TEXT NOT NULL,
    title             TEXT NOT NULL,
    description       TEXT,
    audio_key         TEXT,
    cover_url         TEXT,
    duration_sec      INTEGER NOT NULL DEFAULT 0,
    status            TEXT NOT NULL DEFAULT 'draft'
                      CHECK (status IN ('draft', 'scheduled', 'published')),
    is_premium        BOOLEAN NOT NULL DEFAULT false,
    price_minor       BIGINT NOT NULL DEFAULT 0,
    currency          TEXT NOT NULL DEFAULT 'GHS',
    is_early_access   BOOLEAN NOT NULL DEFAULT false,
    scheduled_at      TIMESTAMPTZ,
    published_at      TIMESTAMPTZ,
    plays             BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key   TEXT,
    request_hash      TEXT,
    CONSTRAINT chk_premium_price CHECK (NOT is_premium OR price_minor > 0)
);

CREATE INDEX ix_studio_episode_artist ON studio_episode (artist_id);
CREATE INDEX ix_studio_episode_show   ON studio_episode (show_id);
CREATE INDEX ix_studio_episode_due    ON studio_episode (status, scheduled_at);

-- Idempotency: a replay of (artist_id, idempotency_key) must resolve to the same episode, no
-- second media upload (Studio ADD §9). Partial index — most rows never carry a key.
CREATE UNIQUE INDEX ux_studio_episode_idem
    ON studio_episode (artist_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
