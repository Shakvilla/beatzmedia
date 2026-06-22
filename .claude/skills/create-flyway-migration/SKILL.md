---
name: create-flyway-migration
description: Author a forward-only Flyway migration for a BeatzClik module — allocate the next version in the module's band with next-migration-version.sh, name it V<n>__<snake_desc>.sql, and apply the project's PostgreSQL conventions. Use whenever a WU adds or changes schema.
allowed-tools: Bash, Read, Write, Edit, Glob
---

# Create a Flyway migration

Migrations are **forward-only, additive, never edited once merged**. Full policy:
`backend/docs/cross-cutting/data-and-migrations.md`.

## 1. Allocate the version (avoid parallel-agent collisions)
Each module owns a numeric band. Pick the next free number **in your module's band**:
```bash
backend/scripts/next-migration-version.sh <module>
```
Bands: platform `V1xx` · identity `V2xx` · catalog `V3xx` · playback `V4xx` · library `V5xx` ·
commerce `V6xx` · payments `V7xx` · store/podcasts/events `V8xx` · notifications/studio/admin/analytics/audit `V9xx`.
Increment by 1 within the band; never reuse a number from another module.

## 2. Create the file
`backend/src/main/resources/db/migration/V<n>__<snake_desc>.sql` (double underscore). One logical change per migration (a table + its indexes/constraints, or one additive column + backfill). Repeatable seed is the single `R__seed_dev_data.sql` (dev/test only) — update it idempotently (`INSERT ... ON CONFLICT`) if dev-data shape changed.

## 3. Apply the conventions (CI/review rejects deviations)
- Naming: `snake_case`; PK `id TEXT`; FK `<entity>_id`; **money `*_minor BIGINT`**; durations `*_sec INT`; timestamps `*_at TIMESTAMPTZ` (UTC); booleans `is_*`/`has_*`.
- IDs are domain-generated opaque strings (UUIDv7/ULID) → `TEXT`. Never `bigserial`/identity.
- Enums → `TEXT` with a **named** `CHECK (col IN (...))` constraint (`<table>_<col>_chk`), not native PG ENUM.
- **Within-module FKs: yes. Cross-module FKs: no** — cross refs are bare `TEXT` id columns, resolved via ports.
- Add an **index for every documented filter/lookup** in the module ADD (`?status=`, `?type=`, `?q=`, unique lookups, FK joins). `order` is reserved — quote `"order"` or prefer `customer_order`. `pg_trgm` indexes need the extension migration first.
- Money/ledger: amounts `*_minor BIGINT`; ledger entries balanced (`Σ debits = Σ credits` per `txn_id`) and append-only — corrections are compensating entries, never row edits. `audit_entry` is append-only.

## 4. Additive-first / expand-contract
New tables, nullable columns, indexes, widened CHECK sets are safe. For a new NOT NULL column on a populated table: add nullable → backfill → add NOT NULL in a **later** migration. Renames/drops are expand→dual-write→backfill→switch→contract across separate migrations.

## 5. Never edit a merged migration
Once a `V` file is merged/applied anywhere its checksum is frozen. To fix a mistake, write a **new** `V<n+1>` that corrects it. Editing a shipped file breaks every migrated DB (checksum mismatch → boot failure).

## 6. Migration test note
The set must apply cleanly on an empty Testcontainers Postgres and `flyway.validate()` must pass (no out-of-order, no checksum drift). Add/confirm a `*MigrationIT` that boots a fresh container, runs Flyway, and asserts `validate()`. Update the version registry (data-and-migrations §7) for the module's row in the same PR. Then run run-verification-gate.
