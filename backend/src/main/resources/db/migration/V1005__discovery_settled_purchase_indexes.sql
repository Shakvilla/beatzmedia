CREATE INDEX idx_discovery_order_grant_recent ON order_grant_posting (posted_at DESC, order_id);
CREATE INDEX idx_discovery_order_line_target ON order_line (kind, ref_id, order_id);
