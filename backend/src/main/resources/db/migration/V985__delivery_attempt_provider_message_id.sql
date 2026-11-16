-- WU-EXT-5: retain the provider's acceptance reference separately from delivery status.
ALTER TABLE delivery_attempt ADD COLUMN provider_message_id VARCHAR(255);
