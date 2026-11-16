# AGENTS.md — BeatzClik Repo Quick Reference

## Repo Structure

- **Frontend/** — Finished React SPA (Vite, TypeScript, Tailwind). Functional spec for the backend.
- **backend/** — Quarkus service (`beatzmedia`), Java 25, PostgreSQL 16. Implements `API-CONTRACT.md`.
- **CLAUDE.md** — Full project memory, golden rules, conventions. Read first for any backend work.
- **BACKEND-PRD.md** — Requirements catalog (HLFR/LLFR/WU IDs). Source of truth for what to build.
- **API-CONTRACT.md** + **Frontend/src/types/index.ts** — Exact shapes the API must serve.

## Essential Commands

### Backend (run from `backend/`)

```bash
# Dev mode (live reload, starts PostgreSQL via Testcontainers)
./mvnw quarkus:dev

# Full local gate (before any PR)
cd backend && bash scripts/verify.sh && bash scripts/smoke.sh

# Quick inner-loop (skip integration tests)
bash scripts/verify.sh --fast

# Skip formatting check
bash scripts/verify.sh --no-format

# Run single test class
./mvnw test -Dtest=MoneyTest

# Run ArchUnit tests only
./mvnw test -Dgroups=arch

# Run contract tests only
./mvnw verify -DskipITs=false -Dgroups=contract

# Coverage gate (requires Docker for Testcontainers)
./mvnw verify -DskipITs=false -Pcoverage

# Package (for Docker image)
./mvnw package -DskipTests

# Next migration version number
bash scripts/next-migration-version.sh

# Fix Spotless formatting
./mvnw spotless:apply
```

### Frontend (run from `Frontend/`)

```bash
# Dev server (proxies /v1 to Quarkus at 127.0.0.1:8080)
npm run dev

# Typecheck + build (CI gate — tsc -b, NOT tsc --noEmit)
npm run build

# Unit tests
npx vitest run

# Lint (NOT run in CI yet — ~219 pre-existing findings)
npm run lint
```

### Full Stack

```bash
# Docker Compose (from repo root once docker-compose.yml exists)
docker compose up

# Smoke test (boots/checks stack, then stops containers; preserves Postgres/MinIO data)
bash backend/scripts/smoke.sh

# Keep stack and containers running after smoke test
bash backend/scripts/smoke.sh --keep

# Explicit destructive reset: delete Postgres and MinIO data volumes after smoke test
bash backend/scripts/smoke.sh --destroy-volumes
```

## Architecture Quick Facts

- **Hexagonal architecture**: `adapter → application → domain`. Domain imports NO framework.
- **Package structure**: `backend/src/main/java/org/shakvilla/beatzmedia/<module>/` with `adapter/in/`, `adapter/out/`, `application/`, `domain/` subpackages.
- **Inbound adapters** (REST resources) never import each other. Modules never read another module's tables.
- **Cross-module communication**: call input ports or react to domain events.

## Non-Negotiable Rules (CI Enforces)

- **Money**: stored as integer pesewas. API uses `{ amount, currency: "GHS" }`. Constants from `PlatformSettings`, never hard-coded.
- **Ownership**: granted only on confirmed payment settlement. Album/season purchases expand to all tracks/episodes.
- **Preview**: server-enforced 30s preview for for-sale + not-owned content.
- **Ledger**: always balanced double-entry. Idempotency keys on every money/side-effect POST.
- **Audit**: every privileged mutation appends an AuditEntry.

## Testing Requirements

- **Unit tests**: domain + application layers with fakes. Surefire (`-DskipITs=true`).
- **Integration tests**: Testcontainers (PostgreSQL, MinIO). Failsafe (`-DskipITs=false`).
- **Contract tests**: OpenAPI conformance against `API-CONTRACT.md`. Tag: `@Tag("contract")`.
- **ArchUnit**: hexagonal dependency rule. Tag: `@Tag("arch")`.
- **Coverage gates**: Overall ≥80% line, domain ≥90% line + ≥85% branch, application ≥90% line.
- **Spotless**: google-java-format import ordering. CI runs `spotless:check`.

## Migration Conventions

- Forward-only `V<n>__<desc>.sql`. Never edit a merged migration.
- Allocate next version: `bash scripts/next-migration-version.sh`
- Module bands: platform=1xx, identity=2xx, catalog=3xx, etc. (informational, not enforced).

## Dev Server Quirks

- **Frontend proxy**: Vite proxies `/v1` to `http://127.0.0.1:8080` (NOT localhost — IPv4 vs IPv6).
- **Backend CORS**: allows `localhost:5173` and `localhost:4173` by default.
- **JWT dev keys**: classpath resources `jwt/dev-public.pem` and `jwt/dev-private.pem`.
- **Testcontainers**: PostgreSQL auto-provisions in `%test` profile via Quarkus Dev Services.
- **Scheduler disabled in tests**: `%test.quarkus.scheduler.enabled=false`.

## CI Status Checks (Required for Merge)

`build`, `unit-tests`, `integration-tests`, `contract-test`, `archunit`, `coverage-gate`, `spotless-lint`, `security-scan`, `migration-test`, `compose-smoke`, `frontend`

## Human Gates (Pause and Ask)

- **OQ-2** tip fee % and **OQ-4** royalty model (affect money math). Defaults applied.
- **Deploy secrets** (payment provider prod keys, infra credentials).
- Any change contradicting frontend/contract or new structural decision.

## Node Version

- Frontend requires Node 22.17.1 (see `Frontend/.nvmrc`).
- Backend requires Java 25 (Temurin in CI).

## Useful References

- `backend/docs/sdlc/agent-workflow.md` — the autonomous loop playbook.
- `backend/docs/sdlc/testing-strategy.md` — test layering, coverage gates.
- `backend/docs/sdlc/branching-and-pr.md` — branch naming, commit conventions.
- `backend/docs/cross-cutting/data-and-migrations.md` — Flyway policy.
- `backend/docs/cross-cutting/security-authz.md` — JWT, RBAC, password handling.
