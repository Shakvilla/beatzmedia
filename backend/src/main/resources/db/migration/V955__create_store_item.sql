-- V955__create_store_item.sql
-- Store module (WU-STO-1): store_item. Store ADD §7.
-- No cross-module FK: artist_id is a bare reference column into catalog's artist_profile.
-- One index per documented filter/sort key (no full-table sort, Store ADD §5.1).

CREATE TABLE store_item (
    id              VARCHAR(40)  PRIMARY KEY,
    type            VARCHAR(16)  NOT NULL,            -- StoreItemType
    title           VARCHAR(200) NOT NULL,
    artist_name     VARCHAR(200) NOT NULL,
    artist_id       VARCHAR(40),                      -- ref to catalog artist (no cross-module FK)
    image           TEXT         NOT NULL,
    price_minor     BIGINT       NOT NULL CHECK (price_minor >= 0),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'GHS',
    genre           VARCHAR(16),
    badges          JSONB        NOT NULL DEFAULT '[]',
    description     TEXT,
    popularity      INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    quality         VARCHAR(64),                      -- TRACK/ALBUM hi-fi
    drops_at        TIMESTAMPTZ,                      -- EXCLUSIVE
    stock_remaining INTEGER      CHECK (stock_remaining >= 0),  -- EXCLUSIVE / MERCH (INV-STORE-C)
    CONSTRAINT chk_store_item_type CHECK (type IN ('TRACK','ALBUM','BEAT_LICENSE','MERCH','EXCLUSIVE'))
);

CREATE INDEX idx_store_item_type        ON store_item (type);
CREATE INDEX idx_store_item_genre       ON store_item (genre);
CREATE INDEX idx_store_item_popularity  ON store_item (popularity DESC NULLS LAST);  -- sort=popular
CREATE INDEX idx_store_item_created_at  ON store_item (created_at DESC);             -- sort=newest
CREATE INDEX idx_store_item_price_minor ON store_item (price_minor);                 -- sort=price-asc/desc
CREATE INDEX idx_store_item_stock       ON store_item (stock_remaining) WHERE stock_remaining IS NOT NULL;
