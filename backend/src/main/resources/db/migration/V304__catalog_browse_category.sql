-- V304__catalog_browse_category.sql
-- WU-CAT-2: browse_category table (deferred from WU-CAT-1 per V303 comment).
-- Catalog ADD §7 / data-and-migrations §4.1 (band V3xx).

CREATE TABLE browse_category (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    color_class TEXT NOT NULL
);
