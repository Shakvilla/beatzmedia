-- Keep existing whole-second lyric timestamps while allowing LRC subsecond sync values.
ALTER TABLE lyric_line
    ALTER COLUMN t_sec TYPE NUMERIC(12, 3)
    USING t_sec::NUMERIC(12, 3);
