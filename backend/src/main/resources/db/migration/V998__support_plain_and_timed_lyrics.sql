-- Preserve lyric line order independently of optional sync timestamps.
ALTER TABLE lyric_line DROP CONSTRAINT lyric_line_pkey;
ALTER TABLE lyric_line ADD COLUMN position INT;

WITH ordered_lines AS (
    SELECT track_id, t_sec, ROW_NUMBER() OVER (PARTITION BY track_id ORDER BY t_sec) - 1 AS line_position
    FROM lyric_line
)
UPDATE lyric_line AS line
SET position = ordered_lines.line_position
FROM ordered_lines
WHERE line.track_id = ordered_lines.track_id AND line.t_sec = ordered_lines.t_sec;

ALTER TABLE lyric_line ALTER COLUMN position SET NOT NULL;
ALTER TABLE lyric_line ALTER COLUMN t_sec DROP NOT NULL;
ALTER TABLE lyric_line ADD CONSTRAINT lyric_line_pkey PRIMARY KEY (track_id, position);
