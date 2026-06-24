-- V1__platform_settings.sql
-- WU-PLT-1: Platform kernel — platform_settings (single-row config) + feature_flag tables.
-- ADD §7 / analytics-audit-platform.md.

-- platform_settings (single-row, id=1 enforced by CHECK)
CREATE TABLE platform_settings (
    id                   SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    platform_fee_pct     INTEGER     NOT NULL DEFAULT 30,
    creator_share_pct    INTEGER     NOT NULL DEFAULT 70,
    tip_fee_pct          INTEGER     NOT NULL DEFAULT 10,
    bundle_discount_pct  INTEGER     NOT NULL DEFAULT 24,
    payout_day           TEXT        NOT NULL DEFAULT 'Friday',
    payout_minimum_minor BIGINT      NOT NULL DEFAULT 1000,
    service_fee_minor    BIGINT      NOT NULL DEFAULT 50,
    default_currency     TEXT        NOT NULL DEFAULT 'GHS',
    is_maintenance_mode  BOOLEAN     NOT NULL DEFAULT false,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- feature_flag (keyed by FeatureKey enum name)
CREATE TABLE feature_flag (
    key        TEXT        PRIMARY KEY,
    is_enabled BOOLEAN     NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
