# Stage 1 Operator Replay and Recovery Design

**Date:** 2026-11-01  
**Baseline:** `c1ccc7f`  
**Status:** Accepted as `ST1-02`

## Scope and access

The first operator surface is an internal JSON API rather than a new dashboard. Super-admin and
support users may inspect queue health and search publication events. Only super-admin users may
replay a failed event. Replay requires a non-blank reason and produces an append-only audit entry in
the same transaction as the queue-state change.

The API never returns event payloads or stack traces. It exposes event identity, contract version,
aggregate reference, timestamps, status, attempt and replay counts, and a bounded failure summary.
Filters cover status, event type, aggregate ID, and occurrence window with normal platform paging.

## Replay rules

Only terminally failed events may be replayed. The event must still have a registered handler for
its exact event type and schema version. A replay resets the dispatch attempt counter, clears the
claim and failure summary, makes the row immediately available, and increments durable replay
metadata. Completed, pending, or processing events return a conflict rather than being duplicated.

The update is conditional on `status = 'failed'`, so two operators racing to replay the same event
cannot both succeed. The audit entry records actor, event ID, action, reason, and timestamp. If audit
persistence fails, the replay rolls back with it.

## Health and verification

Queue health reports pending, processing, failed, completed-in-the-last-hour, oldest incomplete age,
and a derived state. Any failed event makes the state `degraded`; an oldest incomplete age above five
minutes makes it `delayed`; otherwise it is `healthy`. These states are operational signals, not
publication availability promises.

Tests cover filtering and payload redaction, role enforcement, required reasons, unsupported schema
versions, non-failed replay rejection, concurrent replay safety, audit atomicity, and successful
re-entry into the dispatcher. `ST1-03` remains responsible for parity evidence and removal of the
temporary immediate CDI publication path.
