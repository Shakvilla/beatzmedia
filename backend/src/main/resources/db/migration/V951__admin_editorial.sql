-- V951__admin_editorial.sql
-- WU-ADM-4: admin module — editorial (featured slots, push schedule, curated playlists),
-- LLFR-ADMIN-06.1. Admin band V9xx per data-and-migrations §4.1. No cross-module FKs; editorial
-- feeds /home via a reader port consumed by catalog — this module never reads catalog tables.

CREATE TABLE featured_slot (
    id           TEXT        PRIMARY KEY,
    position     INT         NOT NULL,
    title        TEXT        NOT NULL,
    note         TEXT,
    is_sponsored BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT featured_slot_position_uq UNIQUE (position)
);

CREATE TABLE push_item (
    id           TEXT        PRIMARY KEY,
    day          TEXT        NOT NULL,
    time_label   TEXT        NOT NULL,
    title        TEXT        NOT NULL,
    audience     TEXT        NOT NULL,
    scheduled_at TIMESTAMPTZ
);

CREATE INDEX push_item_scheduled_at_idx ON push_item (scheduled_at);

CREATE TABLE curated_playlist (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE INDEX curated_playlist_name_idx ON curated_playlist (name);
