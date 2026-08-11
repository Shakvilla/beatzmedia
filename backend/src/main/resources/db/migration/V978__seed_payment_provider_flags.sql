-- GAP-13 — per-provider payment enablement.
--
-- One feature_flag row per payment rail, seeded ENABLED so this migration preserves today's
-- behaviour exactly: every method is currently accepted platform-wide, and turning one off must be
-- a deliberate operator action rather than a side effect of deploying this change.
--
-- These rows are NOT optional. PaymentProviderPolicy reads them fail-closed (unlike
-- FeatureFlags.isEnabled, which defaults an unknown key to true), and PaymentProviderFlagsCheck
-- refuses to start the application if any are missing. That combination is deliberate: a rail whose
-- row vanished must not keep charging, and it must not silently decline either — it must stop the
-- deploy, which is the only outcome an operator learns about immediately.
--
-- ON CONFLICT DO NOTHING so a re-run, or an environment where an operator has already toggled a
-- rail off, is not silently re-enabled by a redeploy.
INSERT INTO feature_flag (key, is_enabled, updated_at) VALUES
  ('PROVIDER_MTN',        true, now()),
  ('PROVIDER_TELECEL',    true, now()),
  ('PROVIDER_AIRTELTIGO', true, now()),
  ('PROVIDER_CARD',       true, now()),
  ('PROVIDER_BANK',       true, now())
ON CONFLICT (key) DO NOTHING;
