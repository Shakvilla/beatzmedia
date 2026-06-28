-- V203__create_fan_settings.sql
-- WU-IDN-3: fan_settings table. 1-1 with account; lazy-created with defaults on first read/patch.
-- Identity ADD §7.

CREATE TABLE fan_settings (
    account_id        TEXT PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    theme             TEXT NOT NULL DEFAULT 'system',
    audio_quality     TEXT NOT NULL DEFAULT 'High (256 kbps)',
    streaming_quality TEXT NOT NULL DEFAULT 'High (256 kbps)',
    download_quality  TEXT NOT NULL DEFAULT 'Very high (320 kbps)',
    crossfade         TEXT NOT NULL DEFAULT 'Off',
    data_saver        BOOLEAN NOT NULL DEFAULT FALSE,
    notif_json        JSONB NOT NULL DEFAULT '{"newReleases":true,"playlistUpdates":true,"dropsOffers":false}',
    country           TEXT NOT NULL DEFAULT 'Ghana',
    phone             TEXT
);
