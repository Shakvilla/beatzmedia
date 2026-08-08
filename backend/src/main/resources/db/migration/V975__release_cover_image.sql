-- V975__release_cover_image.sql
-- Gives a release somewhere to keep its cover art.
--
-- WHY. The Studio release wizard has always had a cover-art picker and a "Add cover art before
-- submitting" gate, but the image never left the browser: the wizard held a `blob:` object URL and
-- no request ever carried the file. There was no image upload in the backend at all until
-- POST /v1/studio/podcasts/shows/{id}/cover, and the release table had no column to store one.
--
-- So every uploaded track fell back to the hardcoded '/images/placeholder.jpg' that
-- UploadReleaseTrackService stamps on it — which is what fans actually see on the track row, the
-- artist page and in search.
--
-- Nullable: a draft is created before art is chosen. The cover is required at SUBMIT, which the
-- wizard already enforces — now against something real.

ALTER TABLE "release" ADD COLUMN cover_image TEXT;
