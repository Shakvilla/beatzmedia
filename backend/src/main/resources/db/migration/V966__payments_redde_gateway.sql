-- WU-PAY-6: Redde PSP gateway — MoMo direct debit + card hosted checkout (pluggable).
-- Forward-only; never edits V701..V705. Payments band schema plus the platform PSP_REDDE flag seed.
--
-- Three changes:
--   1. payment_intent.checkout_url — nullable column carrying the Redde hosted-checkout redirect URL
--      for a card intent (null for every direct-charge/MoMo/sandbox intent). Purely for the API
--      response (PaymentIntentView.checkoutUrl); NOT used for any internal lookup — the Redde
--      checkouttransid is stored in the existing provider_ref column so downstream resolution is
--      unchanged.
--   2. redde_clienttransid_seq — Redde requires a client transaction id of <= 10 digits; the app's
--      UUIDv7 ids are far too long, so a dedicated bigint sequence supplies short numeric ids
--      (read modulo 10 digits). A DB sequence (not an in-memory counter) so it survives restarts.
--   3. Seed feature_flag PSP_REDDE = false. FeatureFlagsAdapter.isEnabled() fails OPEN (returns true)
--      for a key with no row, so this MUST be seeded false explicitly — otherwise shipping this code
--      would silently route real charges through Redde with blank credentials before anyone opts in.

ALTER TABLE payment_intent ADD COLUMN checkout_url TEXT;

CREATE SEQUENCE redde_clienttransid_seq;

INSERT INTO feature_flag (key, is_enabled, updated_at)
VALUES ('PSP_REDDE', false, now())
ON CONFLICT (key) DO NOTHING;
