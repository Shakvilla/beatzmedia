-- Public artist profiles count distinct identified listeners over a rolling 28-day window.
-- Processed facts remain in this table, so the rollup job does not affect the count.
CREATE INDEX idx_analytics_play_fact_artist_listener_window
    ON analytics_play_fact (artist_id, occurred_at, account_id)
    WHERE account_id IS NOT NULL;
