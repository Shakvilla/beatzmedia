-- V968__commerce_order_line_display_fields.sql
-- WU-COM-3: order_line gains subtitle/image (nullable, additive) so a settled order's receipt can
-- render the same display data the cart already carries (cart_item.subtitle/.image, V943). Copied
-- from the cart's priced item at checkout time (CheckoutService), alongside the existing title copy.
-- No existing column changes; no data backfill needed (existing rows get NULL, which the frontend
-- already treats as "no subtitle/no image" — same as any other nullable display field).

ALTER TABLE order_line ADD COLUMN subtitle VARCHAR(256);
ALTER TABLE order_line ADD COLUMN image VARCHAR(512);
