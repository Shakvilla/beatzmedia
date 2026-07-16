-- WU-COM-4: persist the hosted-checkout redirect URL on the order so an idempotent checkout replay
-- returns the same URL. Nullable and null for every MoMo/sandbox charge; non-null only for a card
-- charge that requires a Redde hosted-checkout redirect (WU-PAY-6). "order" is a reserved word.
ALTER TABLE "order" ADD COLUMN checkout_url TEXT;
