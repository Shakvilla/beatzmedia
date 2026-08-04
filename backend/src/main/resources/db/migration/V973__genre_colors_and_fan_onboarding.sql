-- V973__genre_colors_and_fan_onboarding.sql
--
-- Three related changes:
--   1. Give genres their tile colours, so the home/search "Browse by mood & genre" tiles are
--      driven by the taxonomy instead of a hardcoded map in the frontend.
--   2. Retire the browse_category kind, now that genres ARE the browse tiles.
--   3. Give fans somewhere to record the genres they picked at onboarding.

-- ---------------------------------------------------------------------------
-- 1. Genre tile colours.
--
-- These nine gradients were CATEGORY_GRADIENT in Frontend/src/routes/search.tsx — a Record keyed by
-- category slug. Tailwind's JIT only scans source, so class names arriving from an API at runtime
-- are purged from the built CSS; that is why the map existed and why the tiles rendered transparent
-- when the keys did not match. The classes are still written out literally in the frontend (see the
-- GENRE_TILE safelist) so the JIT emits them; this column decides WHICH one each genre gets.
-- ---------------------------------------------------------------------------

UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-orange-500 to-amber-400'  WHERE kind = 'genre' AND slug = 'afrobeats';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-purple-500 to-pink-400'   WHERE kind = 'genre' AND slug = 'hiplife';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-green-500 to-teal-400'    WHERE kind = 'genre' AND slug = 'highlife';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-blue-500 to-cyan-400'     WHERE kind = 'genre' AND slug = 'amapiano';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-red-500 to-rose-400'      WHERE kind = 'genre' AND slug = 'drill';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-yellow-500 to-lime-400'   WHERE kind = 'genre' AND slug = 'gospel';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-indigo-500 to-violet-400' WHERE kind = 'genre' AND slug = 'rnb';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-emerald-500 to-green-400' WHERE kind = 'genre' AND slug = 'reggae';
UPDATE taxonomy_term SET color_class = 'bg-gradient-to-br from-slate-500 to-gray-400'    WHERE kind = 'genre' AND slug = 'jazz';

-- ---------------------------------------------------------------------------
-- 2. Retire browse_category.
--
-- V972 migrated the old browse_category TABLE into taxonomy_term as its own kind. Genres now carry
-- colours and serve as the browse tiles directly, so that kind has no consumer. Two overlapping
-- lists an operator must keep in sync is exactly the duplication this work removed, so it goes.
--
-- Any rows are dropped rather than converted: on every environment this list was either empty or a
-- duplicate of the genre names it is being replaced by.
-- ---------------------------------------------------------------------------

DELETE FROM taxonomy_term WHERE kind = 'browse_category';

ALTER TABLE taxonomy_term DROP CONSTRAINT chk_taxonomy_kind;
ALTER TABLE taxonomy_term ADD CONSTRAINT chk_taxonomy_kind
    CHECK (kind IN ('genre', 'podcast_category', 'event_category'));

-- ---------------------------------------------------------------------------
-- 3. Fan onboarding.
--
-- A table of its own rather than columns on fan_settings. fan_settings is app preferences (theme,
-- audio quality, notifications) and is modelled as a 12-arg immutable domain object; taste is a
-- different concern with a different lifecycle, and widening that constructor would ripple through
-- every call site and test for no design gain.
--
-- preferred_genres holds LABELS, matching how release.genre and podcast.category store them, so an
-- admin rename repoints these the same way (TaxonomyRepository.repointUsages).
--
-- completed_at is a timestamp, not a boolean: it answers "has this fan been through onboarding"
-- without conflating it with "do they still have 3 genres" — a fan whose chosen genre is later
-- deleted by an admin must not be dragged back through onboarding.
-- ---------------------------------------------------------------------------

CREATE TABLE fan_preferences (
    account_id       TEXT        PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    preferred_genres TEXT[]      NOT NULL DEFAULT '{}',
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing fans count as already onboarded. The gate is for new accounts; retro-fitting a blocking
-- screen onto people already using the app would be a regression, not a feature.
INSERT INTO fan_preferences (account_id, preferred_genres, completed_at)
SELECT id, '{}', now() FROM account;
