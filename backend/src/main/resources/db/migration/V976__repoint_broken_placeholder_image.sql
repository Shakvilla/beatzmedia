-- V976__repoint_broken_placeholder_image.sql
-- Repoints rows at a placeholder image that actually exists.
--
-- WHY. Tracks and newly provisioned artist profiles were stamped with '/images/placeholder.jpg',
-- but that file was never in the repository — Frontend/public/images/ did not exist at all. So the
-- fallback did not render a neutral tile, it rendered a BROKEN image: the browser reported
-- naturalWidth 0 for every one of them.
--
-- The asset is now Frontend/public/images/placeholder.svg, and the single source of truth for the
-- path is CatalogDefaults.PLACEHOLDER_IMAGE. This carries the existing rows across; without it,
-- everything uploaded before today would keep pointing at the missing file forever.
--
-- Scoped to the exact old value: a row that already has real artwork must not be touched.

UPDATE track
   SET image = '/images/placeholder.svg'
 WHERE image = '/images/placeholder.jpg';

UPDATE artist_profile
   SET image = '/images/placeholder.svg'
 WHERE image = '/images/placeholder.jpg';

-- cover_image is nullable and only ever holds uploaded art, so it never carried the placeholder.
