---
name: implement-work-unit
description: Master spec-driven procedure to build one BeatzClik backend work unit (WU) end to end — read spec, plan, branch, implement in hexagonal layers, migrate, test, verify, and open a PR. Use whenever you are asked to implement, build, or ship a WU-* id from backlog.yaml.
allowed-tools: Read, Edit, Write, Glob, Grep, Bash
---

# Implement a Work Unit (the master loop)

Spec-in, spec-out: read the spec, implement within it, update the spec in the same change.
Loop = read spec → plan → branch → implement → test → verify → PR. Run it for the given `WU-<id>`.

## 1. Read the spec
1. Open `backend/.project/backlog.yaml`; find the WU. Note `module`, `add`, `depends_on`, `llfrs`, `status`, any `human_gate`.
2. Read the module ADD `backend/docs/architecture/<add>` (ports, domain types, adapters, owned tables).
3. Read the matching LLFRs in `BACKEND-PRD.md §6` and the endpoint rows in `backend/docs/cross-cutting/api-and-contract.md §10` (+ `Frontend/src/types/index.ts`).
4. Read `backend/docs/01-conventions-and-standards.md` and `backend/docs/sdlc/agent-workflow.md` if not already in context.

## 2. Ready check + human gates
- **Every `depends_on` WU must be `status: done` (merged).** If any is not done, STOP — do not start; report the blockage (agent-workflow §7.4).
- If the WU has a `human_gate` / touches an `OQ-*` (PRD §12): state the question, options, and **documented default**. Local/mechanical OQs → apply the default, note it in the PR, proceed. Scope/economics OQs (OQ-2 tip fee, OQ-4 royalty) → pause and escalate to a human before building.
- Claim the WU: set `status: in_progress` (via `backend/scripts/progress.sh`).

## 3. Plan (short, concrete — a checklist, not an essay)
List, taken from the ADD + contract:
- **Files** per package tree: `domain`, `application/port/in`, `application/port/out`, application services, `adapter/in/rest` (+ DTOs), `adapter/in/job`, `adapter/out/persistence` (entity+mapper), `adapter/out/integration`.
- **Ports & adapters** added/implemented (verbatim from the ADD).
- **Migration** `V<n>__<snake_desc>.sql` — next free number in the module's band (see create-flyway-migration).
- **Tests** — unit (domain + use cases with fakes), integration (Testcontainers + REST-assured), contract (validate vs frontend types).
- **Events** published/observed (`AFTER_SUCCESS`, idempotent handlers).
Reconcile against the contract: every endpoint, field, status code, and `error.code` matches `api-and-contract.md` + the TS types. Do not invent shapes the UI does not need.

## 4. Branch
`git fetch origin && git switch -c feat/<WU-ID>-<kebab-slug> origin/main` (see branching-and-pr §1.1; type ∈ feat|fix|chore|docs|refactor|test|build|ci).

## 5. Implement (hexagonal — ArchUnit-enforced)
Build inner→outer:
1. **Domain** — records/sealed types, framework-free (no Jakarta/Quarkus/Hibernate). Money in minor units; constants from `PlatformSettings`. Mutate via intention-revealing methods that enforce invariants.
2. **Application** — input ports (use cases) + output ports (repos/gateways/`Clock`/`IdGenerator`/`AuditWriter`); service impls `@Transactional`. Re-check resource ownership here.
3. **Adapters** — REST via write-rest-resource (thin), persistence (JPA entity + mapper, owns only this module's tables, no cross-module FK), integration gateways. Inbound/outbound adapters never import each other.
4. **Migration** — add via create-flyway-migration.
5. **Idempotency + audit** — money/side-effect POSTs honour `Idempotency-Key`; every privileged mutation appends one `AuditEntry` (INV-10).

## 6. Tests
Unit + integration + contract per LLFR acceptance criteria. Cover failure paths and assert the exact `error.code`.

## 7. Verify (mandatory pre-PR gate)
Run run-verification-gate (`verify.sh` + `smoke.sh`). Fix and re-run until fully green. High-stakes WUs (WU-PAY-*, WU-COM-2, ledger/KYC) require a verification sub-agent for money correctness (agent-workflow §6).

## 8. Update spec + open PR
- Update the module ADD in this same PR if behavior changed; record an ADR if a structural decision was made.
- Run open-pull-request (branch, Conventional Commit with WU id, template, labels, auto-merge).
- Set the WU `status: in_review`.

## Definition of Done (CI enforces — all must hold)
- [ ] Unit tests pass (domain + use cases with fakes)
- [ ] Integration tests pass (Testcontainers Postgres/MinIO, REST-assured)
- [ ] Contract conformance green (responses validate vs API-CONTRACT / frontend types)
- [ ] ArchUnit green (no domain→framework, no adapter→adapter, no cross-module table access)
- [ ] Flyway set applies cleanly on an empty DB; `flyway.validate()` passes
- [ ] `docker compose up` boots healthy (`/q/health/ready` 200)
- [ ] Money/side-effect paths idempotent; privileged mutations append an `AuditEntry`
- [ ] Coverage ≥ gate; Spotless clean; no new high/critical security findings
- [ ] Module ADD updated in the same PR (if behavior changed)

See `${CLAUDE_SKILL_DIR}/scripts/checklist.md` for the copy-paste DoD checklist.
