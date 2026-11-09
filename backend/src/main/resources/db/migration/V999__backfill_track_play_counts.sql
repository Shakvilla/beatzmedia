-- Track play counts are the catalog's ranking/read projection of immutable playback events.
-- Backfill existing events once; future increments are applied from PlayRecorded in analytics.
UPDATE track t
SET plays = totals.play_count
FROM (
    SELECT track_id, COUNT(*)::BIGINT AS play_count
    FROM play_event
    GROUP BY track_id
) totals
WHERE t.id = totals.track_id;
