-- V946__podcast_creator_account.sql
-- Podcasts tipping (WU-POD-2): add the owning-creator account to a show so a tip can be routed to a
-- REAL, server-resolved recipient (never a client-supplied id). The tip recipient (the 90% share)
-- is resolved from this column by TipShow before delegating to payments' IssueTip input port.
--
-- Nullable: shows authored before studio (WU-STU-2) / seed shows may have no owning account yet;
-- TipShow rejects a tip to a show with no creator as TIPS_DISABLED rather than posting to a phantom.
-- No FK to identity's account table (hexagonal: podcasts holds no cross-module FKs; references by id).
-- Podcasts ADD §3 / §7.

ALTER TABLE podcast ADD COLUMN creator_account_id TEXT;
