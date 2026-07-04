-- V945__create_podcast_and_episode.sql
-- Podcasts module (WU-POD-1): shows + episodes. Podcasts ADD §7.
-- Money in minor units (pesewas, INV-11); durations in whole seconds; premium/early-access flags.

CREATE TABLE podcast (
  id                       TEXT PRIMARY KEY,
  title                    TEXT        NOT NULL,
  publisher                TEXT        NOT NULL,
  image                    TEXT        NOT NULL,
  category                 TEXT        NOT NULL,
  description              TEXT,
  episode_count            INTEGER     NOT NULL DEFAULT 0,
  popularity               INTEGER     NOT NULL DEFAULT 0,
  season_pass_price_minor  BIGINT,                 -- pesewas; NULL = no season pass
  season_pass_currency     TEXT,                   -- 'GHS' when price set
  supports_tips            BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_pod_category CHECK (category IN
    ('News & Politics','Comedy','Business','Sports','Culture','Tech','Health','Storytelling')),
  CONSTRAINT chk_pod_season_pass CHECK (
    (season_pass_price_minor IS NULL AND season_pass_currency IS NULL) OR
    (season_pass_price_minor >= 0 AND season_pass_currency IS NOT NULL))
);
CREATE INDEX idx_podcast_category ON podcast (category);
CREATE INDEX idx_podcast_popularity ON podcast (popularity DESC);

CREATE TABLE podcast_episode (
  id              TEXT PRIMARY KEY,
  podcast_id      TEXT        NOT NULL REFERENCES podcast (id) ON DELETE CASCADE,
  title           TEXT        NOT NULL,
  image           TEXT        NOT NULL,
  description     TEXT,
  duration_sec    INTEGER     NOT NULL CHECK (duration_sec > 0),
  episode_number  INTEGER,
  is_premium      BOOLEAN     NOT NULL DEFAULT FALSE,
  price_minor     BIGINT,                            -- pesewas; required when premium/early-access
  price_currency  TEXT,
  is_early_access BOOLEAN     NOT NULL DEFAULT FALSE,
  public_at       TIMESTAMPTZ,                       -- when early-access becomes free
  media_asset_id  TEXT,                              -- id into media module (no FK)
  published_at    TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ep_price CHECK (
    ((is_premium OR is_early_access) AND price_minor IS NOT NULL AND price_currency IS NOT NULL)
    OR (NOT is_premium AND NOT is_early_access)),
  CONSTRAINT chk_ep_early CHECK (NOT is_early_access OR public_at IS NOT NULL)
);
CREATE INDEX idx_episode_podcast ON podcast_episode (podcast_id, published_at DESC);
