# BeatzClik — Project Memory (read first)

BeatzClik is a **buy-to-own** music streaming + marketplace for Ghana/Africa. This repo holds a
finished **frontend** (`Frontend/`) and the **backend** we are building (`backend/`, Quarkus service
`beatzmedia`). The frontend is the functional spec; the backend implements `API-CONTRACT.md` so the
SPA swaps mocks for real endpoints **with no visual change**.

You are part of an **autonomous engineering team**. The goal is to build the backend from scratch to
production with **minimal human involvement**, following a spec-driven loop. Specialized teammates
live in `.claude/agents/`; reusable procedures in `.claude/skills/`; entry-point workflows in
`.claude/commands/`; runnable automation in `backend/scripts/`.

## Source of truth (read before coding)

1. **`BACKEND-PRD.md`** (repo root) — requirements: HLFR/LLFR catalog, work units (§8), build order
   (§8.1), invariants (§3.3), open questions (§12). IDs (`HLFR-*`, `LLFR-*`, `WU-*`, `INV-*`, `OQ-*`)
   are canonical — never renumber.
2. **`backend/docs/`** — architecture + SDLC. Start at `backend/docs/README.md`. Per-module designs in
   `backend/docs/architecture/<module>.md`; cross-cutting in `backend/docs/cross-cutting/`; the
   autonomous workflow in `backend/docs/sdlc/agent-workflow.md`.
3. **`API-CONTRACT.md`** + **`Frontend/src/types/index.ts`** — the exact shapes responses must serve.
4. **`backend/.project/backlog.yaml`** — the Work Unit registry that drives the loop (status + deps).

## How the team works (the loop)

`read spec → plan → branch → implement → test → verify → PR → review → merge → update backlog`.
The `tech-lead` orchestrates; it reads `backlog.yaml`, picks the next **READY** WU (status `todo` and
every `depends_on` is `done`), respecting phase order, and delegates to the owner agent. The owner
implements via the `implement-work-unit` skill, runs `run-verification-gate`, then `open-pull-request`.
`code-reviewer` + `security-reviewer` review; CI gates the merge; `scrum-master` updates the backlog.
Run it with `/run-loop` (continuous), `/build-next-wu` (one WU), or `/build-phase <n>`. First-time
setup is `/bootstrap`. Status anytime via `/status`.

## Golden rules (non-negotiable — CI and reviewers enforce)

- **Hexagonal dependency rule:** `adapter → application → domain`. Domain imports **no framework**
  (no Jakarta/Quarkus/Hibernate on domain types). Inbound/outbound adapters never import each other.
  Modules never read another module's tables — call its input port or react to its domain event.
- **Money** is stored in **integer minor units (pesewas)**; API uses `{ amount, currency: "GHS" }`.
  Constants (70/30 split, 90/10 tips, 24% bundle discount, ₵0.50 fee, ₵10 min payout) come from
  `PlatformSettings`, never hard-coded. (INV-11, INV-4, INV-5)
- **Ownership is granted only on confirmed payment settlement** (INV-1); album/season purchases expand
  to all constituent tracks/episodes (INV-2); refunds revoke ownership + clawback (INV-9).
- **Preview is server-enforced**: for-sale + not-owned → 30s preview rendition only (INV-3).
- **Ledger is always balanced** double-entry (INV-6). **Idempotency keys** on every money/side-effect
  POST. **Every privileged mutation appends an AuditEntry** (INV-10).
- **Definition of Done** (per WU): unit + integration + contract tests pass; ArchUnit green; Flyway
  migrations apply on an empty DB; `docker compose up` boots healthy; coverage gate met; Spotless
  clean; the module ADD is updated in the same PR if behavior changed. Full list:
  `backend/docs/01-conventions-and-standards.md` §11.

## Conventions (quick)

- Branches: `feat|fix|chore|test|docs/<WU-ID>-slug`, one WU per branch. Commits: Conventional Commits
  with the WU id in scope, e.g. `feat(identity): WU-IDN-1 account registration`. PRs: one per WU using
  the template. Details: `backend/docs/sdlc/branching-and-pr.md`.
- Migrations: forward-only `V<n>__<desc>.sql`; allocate the next version with
  `backend/scripts/next-migration-version.sh`. Never edit a merged migration.
  Policy: `backend/docs/cross-cutting/data-and-migrations.md`.
- Tests: layering, coverage gates, ArchUnit rules in `backend/docs/sdlc/testing-strategy.md`.
- Verify locally before any PR: `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh`.

## Human gates (when to pause and ask)

Proceed autonomously everywhere **except** these — apply the documented default, flag in `/status`,
and request human confirmation before shipping to production:

- **OQ-2** tip fee % and **OQ-4** royalty model (affect `WU-PAY-3` money math). Defaults applied.
- **Deploy secrets** (payment provider prod keys, infra credentials) — `deploy.yml` stays paused
  until a human provides GitHub Environment secrets.
- Any change that contradicts the frontend/contract, or a new structural decision → record an ADR in
  `backend/docs/00-system-architecture.md` §9 and continue.

## Toolchain

Java 25, Maven (`./backend/mvnw`), Quarkus 3.36.x, PostgreSQL 16, Docker + Docker Compose, `gh` CLI.
Run app locally: `cd backend && ./mvnw quarkus:dev`. Full stack: `docker compose up` (from repo root
once `docker-compose.yml` exists — see `backend/docs/sdlc/environments-and-deployment.md`).
