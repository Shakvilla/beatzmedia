-- WU-CAT-5: Studio release draft flow — genre/description are settable while a release is 'draft'
-- Catalog ADD §5.2 / LLFR-CATALOG-02.2

ALTER TABLE release ADD COLUMN genre       TEXT;
ALTER TABLE release ADD COLUMN description TEXT;
