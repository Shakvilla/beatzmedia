-- V959__studio_podcast_show.sql
-- WU-STU-2: studio_podcast_show — creator-owned podcast show grouping episodes.
-- Studio ADD §7. id/artist_id are TEXT (not native UUID), matching the codebase-wide convention
-- for primary keys populated by the platform IdGenerator (UUIDv7-as-string) — same deviation as
-- studio_profile.artist_id (V958). No FK to studio_profile / artist_profile (cross-module FKs
-- forbidden; matched by convention only).

CREATE TABLE studio_podcast_show (
    id          TEXT PRIMARY KEY,
    artist_id   TEXT NOT NULL,
    title       TEXT NOT NULL,
    category    TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_studio_show_artist ON studio_podcast_show (artist_id);
