# Stage 1 Publication Parity and Outbox Cutover Design

**Date:** 2026-11-02  
**Baseline:** `5bc8027`  
**Status:** Accepted; implementation slice `ST1-03`

## 1. Decision

BeatzClik will make the PostgreSQL transactional outbox the sole trigger for release and podcast
publication projections. The current immediate CDI publication events were retained only as a
dual-delivery migration aid. They will be removed from the four publication application services
after integration tests prove that dispatching the durable messages produces the same catalogue,
search, pricing, album, and podcast state.

The creator-facing mutation remains transactional: the aggregate transition, audit record, and
outbox append commit together. A successful response means publication was durably accepted. The
public read models become eventually consistent and normally converge on the next outbox poll,
within five seconds. The scheduler interval remains unchanged to avoid adding a second wake-up
mechanism or unnecessary database polling during this cutover.

## 2. Data flow and failure semantics

Immediate release approval, scheduled release go-live, release reinstatement, publish-now episode,
draft-to-public episode update, and scheduled episode go-live all append one versioned durable
message in the same database transaction as their aggregate change. They no longer fire
`ReleaseWentLive` or `EpisodePublished` directly.

The outbox dispatcher claims committed messages and invokes the registered version-specific
handler without an enclosing transaction. The handler emits the existing domain event
synchronously. Because no transaction is active, CDI invokes the transactional observers
immediately; each projection service owns its existing `REQUIRES_NEW` transaction. Projection
failures can therefore return to the dispatcher and trigger retry. Handler success records an
inbox receipt and completes the outbox event. Terminal failures remain visible through queue health
and the audited replay endpoint.

The authoritative write model may therefore show `live` or `published` for up to five seconds
before every public projection catches up. This is an accepted consistency window, not a publish
failure. No frontend contract changes are required in this slice; a later UX enhancement may show
a short “Publishing…” state.

## 3. Parity proof

Integration coverage will prove both sides of the boundary. Before dispatch, a publication must
have a pending outbox row and must not depend on an immediate CDI projection. After one dispatcher
run, the event must complete and the same public projections previously driven by the immediate
path must exist with the expected content. The proof covers release album, pricing and search
projections plus podcast catalogue projection.

Application tests will assert one durable append for every publication transition and no immediate
publication event dependency. Dispatcher tests continue to cover duplicate delivery, partial inbox
recovery, expired claims, poison isolation, unsupported versions, and retries. Existing catalogue,
Studio, architecture, migration, formatting, and full unit gates must remain green.

## 4. Cutover and rollback

The change requires no schema migration and preserves the event contracts introduced in `ST1-01`.
Deployment uses the existing five-second scheduler and `ST1-02` operator controls. Operators should
watch pending age, retries, failures, and oldest incomplete age during rollout.

Rollback is a code deployment that restores the immediate CDI fires; durable rows and inbox
receipts remain compatible. Operators must not replay completed events merely to accelerate
visibility because normal dispatch and idempotent recovery already cover that case.
