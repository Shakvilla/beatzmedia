-- V961__studio_settings.sql
-- WU-STU-4: Studio module — creator settings config (Studio ADD §7 / §16).
--
-- NOTE: artist_id is TEXT (not native UUID), matching the codebase-wide convention of TEXT primary
-- keys populated by the platform IdGenerator (UUIDv7-as-string) — see V958__studio_profile.sql's
-- header comment for the same documented deviation from the ADD's illustrative UUID column.
--
-- Only the genuinely backend-owned settings sub-objects are persisted here (notifications,
-- defaults, payouts, privacy, team). Category B fields (sessions, connectedApps, verification,
-- billing, twoFactor, phone, language, timezone, country, email) have no backing subsystem and are
-- served as honest static/derived defaults at the application layer — see studio.md §16. No
-- `security` column: the illustrative §7 SQL included one, but nothing in this WU's scope (no 2FA
-- infrastructure in identity) ever reads or writes it.

CREATE TABLE studio_settings (
    artist_id      TEXT        PRIMARY KEY,
    notifications  JSONB       NOT NULL DEFAULT '{}',
    defaults       JSONB       NOT NULL DEFAULT '{}',
    payouts        JSONB       NOT NULL DEFAULT '{}',
    privacy        JSONB       NOT NULL DEFAULT '{}',
    team           JSONB       NOT NULL DEFAULT '[]',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
