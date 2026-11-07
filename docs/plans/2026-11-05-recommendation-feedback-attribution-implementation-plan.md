# Recommendation Feedback and Attribution Implementation Plan

**Date:** 2026-11-05
**Status:** Completed
**Design:** `docs/plans/2026-11-05-recommendation-feedback-attribution-design.md`
**Work unit:** WU-DIS-3

## 1. Persistence foundation

1. Add a forward-only migration for recommendation attribution, events, and suppressions.
2. Add account/target, expiry, and attribution lookup indexes plus idempotency constraints.
3. Add a Discovery repository port and a native PostgreSQL adapter.
4. Cover matching suppression reads, account-bound attribution lookup, idempotent event insert,
   suppression upsert/Undo, and bounded expiry deletion with repository integration tests.

## 2. Discovery application behavior

1. Add input models for event batches, feedback, Undo, and attribution validation.
2. Add a transactional interaction service with strict action/event validation and account isolation.
3. Filter matching content and source suppressions before ranking and four-type reservation.
4. Create short-lived attribution identities for ranked cards in an independent transaction so
   persistence failure cannot make recommendations unavailable.
5. Return `recommendationId`, `rankingVersion`, and one-based `position` on recommendation items.
6. Add authenticated REST endpoints for event ingestion, feedback, and Undo.
7. Add a bounded scheduled cleanup entry point.

## 3. Engagement attribution

1. Add an optional recommendation ID to listening checkpoint input and persistence.
2. Validate it through a narrow Discovery input port without exposing Discovery tables.
3. Resolve direct-track, release-to-track, and show-to-episode matching.
4. Treat invalid, expired, or unavailable attribution as unattributed listening.
5. Preserve checkpoint idempotency and all existing history/privacy behavior.

## 4. Web behavior

1. Extend recommendation query types and API mutations.
2. Add a small expiring session-storage attribution context.
3. Track impressions after 50% visibility for one second and send bounded event batches.
4. Track card opens without delaying navigation.
5. Add an accessible overflow menu with content and source suppression actions.
6. Apply optimistic removal, query refresh, error rollback, and Undo.
7. Attach a matching recommendation ID to the first engagement checkpoint.

## 5. Verification and documentation

1. Add backend unit, application, repository, and authenticated resource tests.
2. Add frontend query, component, visibility, optimistic-action, and attribution-context tests.
3. Run focused Maven tests and Spotless.
4. Run focused Vitest and ESLint, the production build, and React Doctor.
5. Update API contract, backend PRD, Discovery/Engagement architecture docs, and backlog status.

## Delivery boundary

This plan excludes platform-wide creator blocking, paid placement, purchase/support conversion,
creator-facing recommendation analytics, and a full hidden-content settings screen.
