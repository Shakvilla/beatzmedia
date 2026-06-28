-- V942__audit_entry_actor_name.sql
-- WU-AUD-1: Add actor_name to audit_entry so the read endpoint can display the actor's display
-- name without joining to the account table (cross-module table access is forbidden by §6).
-- The column is nullable so existing rows (written by WU-IDN-4 without actor_name) remain valid.
-- Audit band V9xx per data-and-migrations §4.1.

ALTER TABLE audit_entry
    ADD COLUMN IF NOT EXISTS actor_name TEXT;
