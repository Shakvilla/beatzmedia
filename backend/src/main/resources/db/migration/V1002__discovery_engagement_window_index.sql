-- Bound the discovery metrics read by content identity and time window.
CREATE INDEX idx_listening_checkpoint_discovery_window
    ON listening_checkpoint (content_id, received_at, content_kind, account_id)
    WHERE effective AND event_type IN ('qualified-play', 'completion');
