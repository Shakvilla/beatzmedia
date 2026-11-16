# Stage 1 Durable Publication Signals Design

**Date:** 2026-11-01  
**Baseline:** `abf54a1`  
**Status:** Accepted by the delivery-sequence decision; implementation begins with `ST1-01`.

## 1. Decision and boundaries

BeatzClik will use a PostgreSQL transactional outbox with polling dispatch. Release and episode publication already commit to PostgreSQL, and the platform already owns a clustered scheduler protected by advisory locks. Keeping the signal in the same database makes the content state transition and event append atomic without adding Kafka, Debezium, or a second operational control plane.

The outbox is platform infrastructure. Catalog and Studio publish versioned messages through a platform output port; they do not import persistence types. Each owning module supplies an input adapter that recognizes its event type, validates the schema version, decodes the payload, and invokes the existing idempotent projection observers. The platform dispatcher discovers those handlers through a framework-neutral handler port.

`ST1-01` uses a deliberate dual-write transition: the existing CDI event remains for immediate fan-visible behavior and the same transaction appends a durable event. The dispatcher later replays the durable copy. Existing album, pricing, search, and podcast projections are upserts or state-derived writes, so the replay is a required duplicate-delivery proof. Removing the immediate path is deferred until parity and operations evidence exist; doing so in the first change would combine infrastructure risk with a user-visible timing change.

## 2. Persistence and concurrency

`platform_outbox_event` stores a time-ordered text ID, event type, schema version, aggregate identity, JSONB payload, occurrence time, availability time, claim lease, attempt count, completion time, and a bounded last error. `platform_inbox_receipt` stores `(consumer_name, event_id)` as its primary key. The foreign key prevents receipts for unknown events, while a check constraint prevents invalid schema versions and negative attempts.

Workers atomically claim eligible rows with an `UPDATE … FROM (SELECT … FOR UPDATE SKIP LOCKED LIMIT …) RETURNING` statement. Eligibility includes new rows and expired leases, so a process crash cannot strand work. The claim transaction ends before any handler runs; no database lock is held across projection work. A partial index on `(available_at, occurred_at, id)` covers incomplete rows only, keeping the queue index small as history grows.

Each handler is named and receives at-least-once delivery. A successful handler writes its inbox receipt. If the process crashes after a projection commits but before the receipt, the handler runs again; therefore handler idempotency is mandatory. When every matching handler has a receipt, the event is completed. Handler failure records a bounded error and exponential retry time, releases the claim, and continues with the rest of the claimed batch so one poison event cannot block unrelated publications.

## 3. Event contracts and data flow

The first contracts are `catalog.release-went-live` version 1 and `studio.episode-published` version 1. Payloads contain only the current minimal snapshots: release ID, artist ID and live timestamp; or episode ID, show ID, artist ID and occurrence timestamp. Event type and schema version are columns as well as handler routing inputs, so compatibility checks never depend on guessing the JSON shape.

For an immediate release, scheduled release, publish-now episode, updated draft episode, or scheduled episode sweep, the application transaction performs these steps: transition the aggregate, save it, append audit where applicable, append the outbox message, and retain the current immediate CDI event. A rollback removes both the aggregate change and outbox row. After commit, the existing immediate observers preserve current behavior.

The platform scheduler claims durable messages in small batches. The module handler validates version 1, decodes the payload, and fires the existing domain event. Successful projections produce the inbox receipt and eventually complete the outbox row. Duplicate delivery reaches the same state without duplicate fan records. Unknown event types or unsupported versions fail visibly and retry rather than being silently discarded.

## 4. Observability, recovery, and tests

The dispatcher exposes counters for claimed, completed, retried, failed-handler, and duplicate/inbox-skipped events, plus a dispatch timer. Queue gauges report pending, processing, failed, and oldest incomplete age from a refreshed repository snapshot. Logs include event ID, type, version, aggregate ID, consumer and attempt but never payload contents.

`ST1-01` tests the persistence kernel and the two publication append paths. Required integration cases cover atomic rollback, clean migration, expired-lease recovery, concurrent claims with `SKIP LOCKED`, duplicate handler delivery, partial inbox recovery, poison-event isolation, and unsupported schema versions. Existing catalog and Studio tests continue to prove no public behavior regression.

`ST1-02` will add the audited operator replay API, explicit dead-letter disposition, queue health endpoints, and dashboards. `ST1-03` will run parity evidence, remove the immediate CDI publication path, and make the outbox the sole trigger for publication-dependent projections. A production release remains blocked until those slices demonstrate replay auditability and operational alerting.
