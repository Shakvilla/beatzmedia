# BeatzClik — Autonomous Engineering Team (`.claude/`)

This directory turns the repo into a self-driving engineering org. Launch `claude` at the **repo
root** and the whole team, its skills, and its workflows load automatically.

## The team (`agents/`)

| Agent | Kind | Role |
|---|---|---|
| `tech-lead` | technical | Orchestrator. Picks the next ready Work Unit from `backend/.project/backlog.yaml`, delegates to the right specialist, enforces sequencing & Definition of Done, drives the loop. |
| `product-owner` | non-technical | Owns the PRD & backlog priorities, grooms work units, resolves/raises open questions (`OQ-*`), signs off acceptance criteria. |
| `scrum-master` | non-technical | Runs cadence, keeps `backlog.yaml` status accurate, reports progress, removes blockers, coordinates parallel work. |
| `backend-engineer` | technical | Implements domain + application + inbound REST adapters per the module ADD (hexagonal). |
| `database-engineer` | technical | PostgreSQL schema, Flyway migrations, persistence adapters; guards data integrity & money-in-minor-units. |
| `payments-specialist` | technical | Payments/commerce money paths: intents, webhooks, double-entry ledger, idempotency, payouts/KYC, refunds. |
| `test-engineer` | technical | Unit/integration/contract/ArchUnit tests; coverage gates; provider mocking; invariant tests. |
| `security-reviewer` | technical | AuthN/Z, RBAC, secrets, webhook signatures, rate limiting, dependency/secret scanning; security sign-off. |
| `devops-engineer` | technical | Docker, Compose, GitHub Actions CI/CD, release & deploy, observability wiring. |
| `code-reviewer` | technical | Reviews each PR against the DoD, hexagonal rule, conventions, and contract conformance. |
| `qa-engineer` | technical | End-to-end/smoke journeys, verifies LLFR Given/When/Then acceptance criteria. |
| `doc-writer` | technical | Keeps ADDs/PRD in sync with code, writes ADRs and the changelog. |

## Skills (`skills/`) — reusable procedures any agent can invoke

`implement-work-unit` · `scaffold-module` · `create-flyway-migration` · `write-rest-resource` ·
`run-verification-gate` · `contract-conformance` · `open-pull-request` · `review-pull-request`.

## Workflows (`commands/`) — entry points you type

| Command | What it does |
|---|---|
| `/bootstrap` | One-time setup: verify toolchain, init git/remote, materialize `.github` + `docker-compose.yml` + baseline app, Phase 0 scaffolding, first green build. |
| `/build-next-wu` | Build the single next READY work unit end-to-end (plan -> code -> test -> verify -> PR -> merge). |
| `/build-phase <n>` | Build every work unit in phase `n` in dependency order. |
| `/run-loop` | Continuous autonomous build until a phase/all WUs are done or a human gate is hit. |
| `/status` | Report backlog progress, what's in flight, what's blocked, and any pending human gates. |
| `/ship` | Cut a release of `main` and run the deploy workflow (paused if prod secrets are absent). |
| `/groom` | Product-owner pass: reprioritize, resolve open questions, refine acceptance criteria. |

## Automation (`backend/scripts/`)

`bootstrap.sh` · `verify.sh` (full local gate) · `smoke.sh` (compose up + health) ·
`next-migration-version.sh` · `new-module.sh` (scaffold a bounded context) · `open-pr.sh` ·
`progress.sh` (read/update `backlog.yaml`).

## Guardrails (`settings.json` + `hooks/`)

- **Permissions** pre-approve safe commands (mvnw, git, gh, docker compose, scripts) so the loop runs
  unattended; destructive commands are denied; pushing to `main`, releases, and image pushes `ask`.
- **Hooks**: `guard-bash.sh` (PreToolUse) blocks force-push / `rm -rf /` / direct commits to `main`;
  `format-java.sh` (PostToolUse) best-effort formats edited Java; `session-start.sh` prints the next
  ready WU on startup.

## State & memory

- `backend/.project/backlog.yaml` — the Work Unit registry (status + dependencies) that drives the loop.
- `../CLAUDE.md` — project memory every agent reads first (golden rules, the loop, human gates).

## Human involvement (by design, minimal)

The loop runs unattended except: **OQ-2** (tip fee %) and **OQ-4** (royalty model) for `WU-PAY-3`,
and **production deploy secrets**. Defaults are applied and surfaced in `/status`; ship to prod only
after a human confirms these.
