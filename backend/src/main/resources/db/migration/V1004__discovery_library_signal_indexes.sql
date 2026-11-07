CREATE INDEX idx_discovery_liked_track_recent ON liked_track (track_id, created_at DESC);
CREATE INDEX idx_discovery_saved_album_recent ON saved_album (album_id, created_at DESC);
CREATE INDEX idx_discovery_artist_follow_recent ON followed_artist (artist_id, created_at DESC);
CREATE INDEX idx_discovery_show_follow_recent ON followed_show (show_id, created_at DESC);
