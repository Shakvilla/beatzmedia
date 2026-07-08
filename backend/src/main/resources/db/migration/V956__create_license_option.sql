-- V956__create_license_option.sql
-- Store module (WU-STO-1): license_option — BEAT_LICENSE tiers (LEASE/PREMIUM/EXCLUSIVE).
-- Store ADD §7. UNIQUE (store_item_id, tier): at most one row per tier per item.

CREATE TABLE license_option (
    id            VARCHAR(40)  PRIMARY KEY,
    store_item_id VARCHAR(40)  NOT NULL REFERENCES store_item(id) ON DELETE CASCADE,
    tier          VARCHAR(16)  NOT NULL,              -- LicenseTier
    label         VARCHAR(80)  NOT NULL,
    price_minor   BIGINT       NOT NULL CHECK (price_minor >= 0),
    features      JSONB        NOT NULL DEFAULT '[]',
    terms         VARCHAR(200),
    sort_order    SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT chk_license_option_tier CHECK (tier IN ('LEASE','PREMIUM','EXCLUSIVE')),
    UNIQUE (store_item_id, tier)
);
CREATE INDEX idx_license_option_item ON license_option (store_item_id);
