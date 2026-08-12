-- V974__studio_show_cover_and_description.sql
-- Gives a studio podcast show the fields it needs to become a fan-facing podcast.
--
-- WHY. studio_podcast_show held only (id, artist_id, title, category). The fan-facing `podcast`
-- table requires image NOT NULL, so a studio show could never be projected into it — which is why
-- podcasts created in the Studio have been invisible to fans since the feature shipped. The two
-- blockers were this missing cover and the chk_pod_category CHECK constraint; V972 dropped the
-- constraint, leaving only the cover.
--
-- Both columns are NULLABLE here, deliberately. A show is created early and edited over time, so
-- requiring a cover at creation would block the drafting flow. The cover is instead required at
-- PUBLISH time (PublishPodcastEpisodeService), matching the release flow where cover art is gated
-- at submit rather than at draft-create.

ALTER TABLE studio_podcast_show ADD COLUMN image       TEXT;
ALTER TABLE studio_podcast_show ADD COLUMN description TEXT;
