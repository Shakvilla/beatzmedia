# WU-ENG-1 Implementation Plan

**Design:** `docs/plans/2026-11-02-authenticated-engagement-history-design.md`  
**Branch:** `feat/wu-eng-1-definition`

## Slice 1 — Architecture and schema

- Record the engagement module ADR and add `backend/docs/architecture/engagement.md`.
- Add forward-only migration `V993__authenticated_engagement_history.sql`.
- Add schema verification for keys, constraints, indexes, and the absence of cross-module FKs.

## Slice 2 — Domain and application core

- Add engagement content/event value objects and validation.
- Add session, checkpoint, progress, preference, and aggregate repository contracts.
- Implement session start, checkpoint ingestion, preference updates, erasure, personal reads, and
  aggregate projection services.
- Unit-test idempotency, stale sequences, conflicting replays, progress thresholds, and opt-out.

## Slice 3 — Persistence and integration adapters

- Implement JPA/native-query persistence with preference-first locking, session locking, atomic
  upserts, keyset reads, and `FOR UPDATE SKIP LOCKED` projection.
- Add catalog and podcast content-reader adapters through public application ports.
- Add the scheduled aggregate projector and operational metrics.

## Slice 4 — REST and contract

- Add the authenticated `/v1/me/listening*` endpoints and DTOs.
- Update `API-CONTRACT.md` and OpenAPI coverage.
- Add authenticated flow, tenant isolation, concurrency, erasure-race, and financial-isolation ITs.

## Slice 5 — Web consumer

- Add typed engagement queries and checkpoint delivery to the global player.
- Preserve the independent counted-play call.
- Add Recently Played, podcast Continue Listening, resume, and history settings to existing web
  surfaces without changing anonymous playback behavior.
- Run frontend tests, type checking, and React diagnostics.

## Verification

- Backend unit, architecture, contract, and affected PostgreSQL integration tests.
- Flyway migration validation and formatting checks.
- Frontend lint, typecheck, tests, and production build where available.
- Docker repackaging/rebuild/smoke remain user-owned.
