-- Downloadable releases: may buyers download this release's audio?
--
-- Nullable and WITHOUT a default, deliberately. NULL means "the artist has not chosen yet", which
-- PublishRelease rejects. A default would mean inertia decides: off-by-default quietly makes the
-- platform stream-only, on-by-default means "the artist chooses" degrades to "the artist notices".
ALTER TABLE release ADD COLUMN downloadable boolean;
