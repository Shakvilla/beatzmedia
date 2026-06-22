---
name: review-pull-request
description: Reviewer procedure for a BeatzClik work-unit PR — verify the Definition of Done, hexagonal dependency rule, no cross-module table access, money/idempotency/audit invariants, contract conformance, tests per LLFR, forward-only migrations, and ADD update; then approve or request specific changes (requiring a security reviewer for payments/security). Use when reviewing or /review-ing a PR.
allowed-tools: Bash, Read, Grep, Glob
---

# Review a Pull Request

Review the head commit against the spec and the invariants. Approve only when all hold; otherwise
request specific, actionable changes. Sources: `branching-and-pr.md §6`, `agent-workflow.md §5`.

## 1. Scope & traceability
- Exactly **one WU per PR**, reasonably small (~<400 changed lines excluding generated/migrations) — flag scope creep.
- Body links the `WU-*` id and the LLFRs; `Depends on` WUs are merged. Any `OQ-*` default is noted (scope/economics OQs — OQ-2 tip fee, OQ-4 royalty — must have human resolution, not a unilateral default).

## 2. Definition of Done (truthfully met, not just ticked)
Confirm: unit + integration tests pass; contract conformance green; ArchUnit green; migrations apply on an empty DB + `validate()`; compose boots healthy; coverage ≥ gate; Spotless clean; no new high/critical findings; **module ADD updated in this same PR** if behavior changed (new ADR if a structural decision was made).

## 3. Architecture (beyond what ArchUnit catches)
- Hexagonal dependency rule: `adapters → application → domain`; domain framework-free (no domain types leaking into REST DTOs or carrying ORM annotations); inbound/outbound adapters don't import each other.
- **No cross-module table access / no cross-module FKs** — cross-context data via the owning module's input port or ids/snapshots on events. A repository touches only its module's tables.
- **Thin resources** — DTO→command→port→DTO, no business logic in resources.

## 4. Money & privileged-path invariants
- Money stored in **minor units** (INV-11); decimal conversion only at the REST boundary; constants from `PlatformSettings`, never hard-coded.
- Split/fee math on minor units, half-up, **sum of parts = whole**; ledger balanced `Σ debits = Σ credits` (INV-6) and append-only.
- Ownership granted only on `SETTLED` (INV-1), revoked on refund (INV-9); split-sum ≤ 100 (INV-12).
- **Idempotency** on money/side-effect POSTs (key+body replay = no repeated effect; key+different body = 409); **one `AuditEntry`** per privileged mutation (INV-10).

## 5. Contract & errors
Response shapes match `API-CONTRACT.md` / frontend types; uniform error envelope with stable `error.code`s from the catalog; money/duration/timestamp/count serialization per §4 (no display strings). `/v1` not broken (no removed/renamed/retyped fields).

## 6. Tests & migrations
Tests cover the LLFR acceptance criteria (failure paths assert exact `error.code`), not just line count. Migrations forward-only, additive, named CHECK enums, indexes for documented filters; no edited merged migration. Secrets/PII never logged.

## 7. Decision
- **Approve** when everything above holds and checks are green.
- **Request changes** with specific file/line items otherwise. If you request changes twice without resolution, or lack confidence, set `needs-human`.
- **Require a security reviewer** (human approval) when the PR carries `area:payments` or `area:security`, is a breaking change, has a destructive/ledger migration, or `security-scan` raises a new high/critical finding. Money correctness on WU-PAY-*/WU-COM-2 should be re-derived by a verification sub-agent before approval.
