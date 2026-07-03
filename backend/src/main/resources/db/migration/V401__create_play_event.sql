-- V401__create_play_event.sql
-- WU-PLY-1: Playback — play_event table + rollup-oriented indexes.
-- ADD §7 / BACKEND-PRD §6.3 LLFR-PLAYBACK-01.2.
--
-- NOTE: id is TEXT (not native UUID) to match the codebase-wide convention of TEXT primary keys
-- populated by the platform IdGenerator (UUIDv7-as-string), e.g. audit_entry, media_asset,
-- account. The ADD's illustrative SQL used UUID; this migration follows the established
-- convention instead (see playback.md update in this same change).

CREATE TABLE play_event (
    id              TEXT        PRIMARY KEY,
    account_id      TEXT        NULL,          -- opaque ref; NULL for anonymous plays
    track_id        TEXT        NOT NULL,      -- opaque ref to catalog track
    at              TIMESTAMPTZ NOT NULL,
    full_vs_preview TEXT        NOT NULL CHECK (full_vs_preview IN ('full','preview')),
    source          TEXT        NOT NULL DEFAULT 'player'
                                CHECK (source IN ('player','preview','autoplay'))
);

-- Rollup-oriented indexes (consumed by analytics WU-ANA-1):
CREATE INDEX idx_play_event_track_at        ON play_event (track_id, at);
CREATE INDEX idx_play_event_at              ON play_event (at);
CREATE INDEX idx_play_event_account_track_at ON play_event (account_id, track_id, at);
