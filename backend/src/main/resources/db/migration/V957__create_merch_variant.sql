-- V957__create_merch_variant.sql
-- Store module (WU-STO-1): merch_variant — MERCH configurable attributes (e.g. Size, Colour).
-- Store ADD §7.

CREATE TABLE merch_variant (
    id            VARCHAR(40)  PRIMARY KEY,
    store_item_id VARCHAR(40)  NOT NULL REFERENCES store_item(id) ON DELETE CASCADE,
    label         VARCHAR(80)  NOT NULL,
    options       JSONB        NOT NULL DEFAULT '[]',
    sort_order    SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_merch_variant_item ON merch_variant (store_item_id);
