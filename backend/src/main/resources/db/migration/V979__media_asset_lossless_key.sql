-- Downloadable releases: the FLAC rendition a download hands over.
--
-- Nullable because it is produced after READY — playback must not wait on a FLAC transcode that
-- only downloads need. An asset with no lossless_key answers 409 DOWNLOAD_NOT_READY rather than
-- silently serving the 128k AAC full rendition.
ALTER TABLE media_asset ADD COLUMN lossless_key varchar(255);
