-- Existing content stays unassigned. Country is supplied explicitly by the creator.
ALTER TABLE release ADD COLUMN origin_country VARCHAR(2);
ALTER TABLE release ADD CONSTRAINT ck_release_origin_country
    CHECK (origin_country IS NULL OR origin_country ~ '^[A-Z]{2}$');

ALTER TABLE studio_podcast_show ADD COLUMN origin_country VARCHAR(2);
ALTER TABLE studio_podcast_show ADD CONSTRAINT ck_studio_show_origin_country
    CHECK (origin_country IS NULL OR origin_country ~ '^[A-Z]{2}$');
