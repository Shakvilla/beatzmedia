-- V962__account_verified.sql
-- WU-ADM-2: admin user administration — LLFR-ADMIN-02.2 (verify artist).
--
-- Adds the `verified` flag to identity's `account` table. There is no such column anywhere in the
-- codebase prior to this WU; admin's "verify artist" action needs somewhere to persist the badge
-- state. Owned by `identity` (its table) even though the mutation is admin-driven, per the
-- hexagonal rule: admin calls identity's input port, never writes identity's tables directly.
-- Defaults FALSE for all existing + new rows; no "only artists can be verified" DB constraint (a
-- fan account being verified is a harmless no-op-ish state per admin ADD WU-ADM-2 as-built notes).

ALTER TABLE account ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
