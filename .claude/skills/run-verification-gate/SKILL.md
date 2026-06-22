---
name: run-verification-gate
description: Run the full local pre-PR verification gate for the BeatzClik backend — verify.sh (spotless, compile, unit, integration, ArchUnit, coverage, contract, migration) then smoke.sh (compose up + /q/health/ready) — interpreting failures and re-running until green. Use before opening any PR and after any code/migration change.
allowed-tools: Bash, Read, Edit, Grep
---

# Run the verification gate (pre-PR, mandatory)

Do not rely on CI to discover failures. Run the same gate CI runs, locally, until fully green.
This maps directly to the Definition of Done. Sources: `agent-workflow.md §6`, `branching-and-pr.md §4`.

## 1. Full build + quality gate
```bash
backend/scripts/verify.sh
```
This runs: Spotless (google-java-format) check, compile (Java 25), unit tests, integration tests (Testcontainers Postgres/MinIO + REST-assured), ArchUnit (hexagonal dependency rule), the coverage gate, the contract test (`*ContractTest` vs API-CONTRACT.md / frontend types), and the migration test (`*MigrationIT` — Flyway forward-only apply + `validate()` on an empty DB).

## 2. Compose smoke
```bash
backend/scripts/smoke.sh
```
Brings the stack up (`docker compose up -d --wait`), probes `http://localhost:8080/q/health/ready` (expect 200), optionally exercises the WU's new endpoints, then tears down (`docker compose down -v`).

## 3. Interpret failures and fix
Re-run until every step is green. Common failures:
- **Spotless** → run the format step (`./mvnw spotless:apply`) and re-verify.
- **ArchUnit** → a layering violation: domain importing a framework, an adapter importing another adapter, or a repository touching another module's tables. Move code to the correct layer; resolve cross-module needs via the owning module's input port.
- **Contract test** → response shape/field/`error.code`/money-or-duration serialization diverges from the TS types. Fix the DTO/mapper (see write-rest-resource); never change `/v1` breakingly.
- **Migration test** → out-of-order/checksum drift (edited a merged migration — fix forward with a new version) or band collision (reallocate via next-migration-version.sh).
- **Integration / smoke** → check Testcontainers/compose logs; confirm Flyway applied and health contributors are green.
- **Coverage** → add meaningful tests for the LLFR acceptance criteria, not filler.

## 4. High-stakes WUs
For WU-PAY-*, WU-COM-2 (settlement→grant), WU-PAY-5 (refunds/clawback), and any ledger/KYC WU, also have a verification sub-agent adversarially re-run this gate and re-derive the 70/30 & 90/10 split math on minor units, assert `Σ debits = Σ credits` (INV-6), ownership granted only on `SETTLED` (INV-1) and revoked on refund (INV-9), and idempotency replays produce no repeated effect. The implementing agent does not self-certify money correctness.

Only when **every** step is green do you proceed to open-pull-request.
