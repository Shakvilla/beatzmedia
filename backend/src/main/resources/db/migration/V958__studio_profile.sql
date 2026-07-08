-- V958__studio_profile.sql
-- WU-STU-1: Studio module — creator profile (studio_profile table only; studio_settings /
-- studio_podcast_show / studio_episode land in later WUs WU-STU-2/3/4). Studio ADD §7.
--
-- NOTE: artist_id is TEXT (not native UUID) to match the codebase-wide convention of TEXT primary
-- keys populated by the platform IdGenerator (UUIDv7-as-string), e.g. account.id, track.id,
-- cart.account_id (see V401/V943 for the same documented deviation). The ADD's illustrative SQL
-- used UUID; this migration follows the established convention instead (studio.md updated in this
-- same change).

CREATE TABLE studio_profile (
    artist_id         TEXT        PRIMARY KEY,
    username          TEXT        NOT NULL,
    display_name      TEXT        NOT NULL,
    hometown          TEXT,
    genres            TEXT[]      NOT NULL DEFAULT '{}',
    bio               TEXT,
    avatar_url        TEXT,
    banner_url        TEXT,
    links             JSONB       NOT NULL DEFAULT '{}',
    shows             JSONB       NOT NULL DEFAULT '[]',
    featured_track_id TEXT,            -- id only; resolved via catalog port (no FK)
    booking_email     TEXT,
    press_assets      JSONB       NOT NULL DEFAULT '[]',
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- USERNAME_TAKEN backstop (Studio ADD §9): case-insensitive global uniqueness.
CREATE UNIQUE INDEX ux_studio_profile_username ON studio_profile (lower(username));
