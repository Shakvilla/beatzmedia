-- V3__create_media_asset.sql
-- WU-MED-1: Media pipeline — media_asset table + indexes + CHECK constraints.
-- ADD §7 / BACKEND-PRD §6.14.

CREATE TABLE media_asset (
    id            VARCHAR(40)  PRIMARY KEY,
    owner_ref     VARCHAR(80)  NOT NULL,
    kind          VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    duration_sec  INTEGER,
    original_key  VARCHAR(255) NOT NULL,
    hls_key       VARCHAR(255),
    preview_key   VARCHAR(255),
    content_hash  VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_kind   CHECK (kind   IN ('AUDIO', 'ARTWORK')),
    CONSTRAINT chk_media_status CHECK (status IN ('UPLOADING', 'TRANSCODING', 'READY', 'ERROR'))
);

CREATE INDEX idx_media_asset_owner_ref ON media_asset (owner_ref);
CREATE INDEX idx_media_asset_status    ON media_asset (status);

-- S1: Unique index preventing concurrent identical uploads from both inserting.
-- WHERE content_hash IS NOT NULL: rows without a hash (e.g. before hashing completes) are exempt.
-- The application layer handles the unique-violation on retry by returning the existing handle.
CREATE UNIQUE INDEX uidx_media_asset_owner_content
    ON media_asset (owner_ref, content_hash)
    WHERE content_hash IS NOT NULL;
