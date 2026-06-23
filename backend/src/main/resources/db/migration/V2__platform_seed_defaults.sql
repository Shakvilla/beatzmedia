-- V2__platform_seed_defaults.sql
-- WU-PLT-1: Seed the single platform_settings row with defaults and the 5 feature flags.
-- ADD §7 / analytics-audit-platform.md.
-- This migration applies on every environment; it is NOT a repeatable seed (R__*).

-- Insert the canonical settings row (id=1).
-- Values: platformFeePct=30, creatorSharePct=70, tipFeePct=10, bundleDiscountPct=24,
--         payoutDay='Friday', payoutMinimumMinor=1000 (₵10), serviceFeeMinor=50 (₵0.50),
--         defaultCurrency='GHS', maintenanceMode=false.
INSERT INTO platform_settings (
    id,
    platform_fee_pct,
    creator_share_pct,
    tip_fee_pct,
    bundle_discount_pct,
    payout_day,
    payout_minimum_minor,
    service_fee_minor,
    default_currency,
    is_maintenance_mode,
    updated_at
) VALUES (
    1,
    30,
    70,
    10,
    24,
    'Friday',
    1000,
    50,
    'GHS',
    false,
    now()
) ON CONFLICT (id) DO NOTHING;

-- Seed the five feature flags (ADD §3.3 / PRD §1.4).
-- tipping=true (active), fanMessaging=false (ships disabled per PRD §1.4).
INSERT INTO feature_flag (key, is_enabled, updated_at) VALUES
    ('ARTIST_SIGNUPS', true,  now()),
    ('PODCASTS',       true,  now()),
    ('EVENTS',         true,  now()),
    ('TIPPING',        true,  now()),
    ('FAN_MESSAGING',  false, now())
ON CONFLICT (key) DO NOTHING;
