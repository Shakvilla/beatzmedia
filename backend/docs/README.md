# BeatzClik Backend — Architecture & SDLC Documentation

This `docs/` tree is the **engineering source of truth** for the BeatzClik backend (`beatzmedia`,
Quarkus). It is written to be executed by **Claude Code AI agents** with minimal human involvement:
agents read these documents, plan work units, implement within the defined architecture, write tests,
open pull requests, and ship to GitHub through the automated SDLC defined under `sdlc/`.

The documents derive from and must stay consistent with:

- **`../../BACKEND-PRD.md`** — the root Product Requirements Document (HLFR/LLFR catalog, work units,
  traceability). Requirements IDs (`HLFR-*`, `LLFR-*`, `WU-*`, `INV-*`, `OQ-*`) are referenced
  throughout these docs and must not be renumbered.
- **`../../API-CONTRACT.md`** — the REST contract derived from the finished frontend.
- **`../../Frontend/src/types/index.ts`** and `Frontend/src/lib/*-data.ts` — the authoritative domain
  shapes the API must serve unchanged.

## How an agent should use these docs

1. Start at **`sdlc/agent-workflow.md`** — the loop playbook (read → plan → branch → build → test →
   PR → review → merge) and the per-work-unit definition of done.
2. Read **`00-system-architecture.md`** for the whole-system view, module map, dependency rule, and
   technology choices, then **`01-conventions-and-standards.md`** for code layout and shared kernels.
3. Pick a work unit from the PRD (§8) honouring the build order in **`00-system-architecture.md` §8 /
   `sdlc/agent-workflow.md`**. Open the matching module ADD under `architecture/` and the relevant
   cross-cutting guide(s).
4. Implement strictly within the ports/adapters and schema described; add Flyway migrations; write
   unit + integration + contract tests; keep the hexagonal dependency rule green (ArchUnit).
5. Follow **`sdlc/branching-and-pr.md`** and let **`sdlc/ci-cd-github-actions.md`** gate the merge.

## Index

### Foundations
- [`00-system-architecture.md`](00-system-architecture.md) — C4 context/container, module map,
  hexagonal dependency rule, cross-module events, technology stack, system-wide build order.
- [`01-conventions-and-standards.md`](01-conventions-and-standards.md) — package layout, naming,
  shared kernel (Money, error model, pagination, ids/clock), coding standards, definition of done.

### Module architecture design docs (`architecture/`)
Each follows [`architecture/_TEMPLATE.md`](architecture/_TEMPLATE.md).

| Doc | Context | PRD §6 | Key HLFRs |
|---|---|---|---|
| [`identity.md`](architecture/identity.md) | Identity & Access | 6.1 | IDENTITY-01..03 |
| [`catalog.md`](architecture/catalog.md) | Catalog & Releases | 6.2 | CATALOG-01..02 |
| [`playback.md`](architecture/playback.md) | Playback & Streaming | 6.3 | PLAYBACK-01 |
| [`library.md`](architecture/library.md) | Library & Collection | 6.4 | LIBRARY-01 |
| [`commerce.md`](architecture/commerce.md) | Commerce / Orders & Ownership | 6.5 | COMMERCE-01..02 |
| [`payments.md`](architecture/payments.md) | Payments & Payouts | 6.6 | PAYMENTS-01..05 |
| [`store.md`](architecture/store.md) | Store (marketplace) | 6.7 | STORE-01 |
| [`podcasts.md`](architecture/podcasts.md) | Podcasts | 6.8 | PODCAST-01..02 |
| [`events.md`](architecture/events.md) | Events & Ticketing | 6.9 | EVENTS-01 |
| [`notifications.md`](architecture/notifications.md) | Notifications | 6.10 | NOTIF-01..02 |
| [`studio.md`](architecture/studio.md) | Studio (creator) | 6.11 | STUDIO-01..04 |
| [`admin.md`](architecture/admin.md) | Admin / Moderation | 6.12 | ADMIN-01..11 |
| [`search.md`](architecture/search.md) | Search & discovery indexing | 6.13 | SEARCH-01 |
| [`media.md`](architecture/media.md) | Media pipeline | 6.14 | MEDIA-01 |
| [`analytics-audit-platform.md`](architecture/analytics-audit-platform.md) | Analytics, Audit, Platform kernel | 6.15 | ANALYTICS-01, AUDIT-01, PLATFORM-01 |

### Cross-cutting guides (`cross-cutting/`)
- [`security-authz.md`](cross-cutting/security-authz.md) — JWT, roles/scopes, RBAC enforcement,
  password/KYC handling, webhook signatures, rate limiting, secrets.
- [`media-pipeline.md`](cross-cutting/media-pipeline.md) — upload → validate → transcode → signed
  delivery; the server-side 30s preview enforcement.
- [`observability.md`](cross-cutting/observability.md) — health, metrics, tracing, logging, SLOs.
- [`data-and-migrations.md`](cross-cutting/data-and-migrations.md) — Postgres conventions, Flyway
  policy, money in minor units, seeding, backups.
- [`api-and-contract.md`](cross-cutting/api-and-contract.md) — REST conventions, error envelope,
  pagination, versioning, OpenAPI generation & contract testing against `API-CONTRACT.md`.

### SDLC & automation (`sdlc/`)
- [`agent-workflow.md`](sdlc/agent-workflow.md) — the autonomous read→plan→build→test→ship loop and
  per-WU definition of done.
- [`branching-and-pr.md`](sdlc/branching-and-pr.md) — branch naming, commit/PR templates, required
  checks, auto-merge, semantic versioning.
- [`ci-cd-github-actions.md`](sdlc/ci-cd-github-actions.md) — concrete GitHub Actions workflows:
  build, test, ArchUnit, contract tests, image build/publish, deploy.
- [`testing-strategy.md`](sdlc/testing-strategy.md) — unit/integration/contract/e2e layering,
  coverage gates, Testcontainers, test data.
- [`environments-and-deployment.md`](sdlc/environments-and-deployment.md) — Compose, profiles,
  containerization, release & rollback.

## Conventions for keeping docs in sync

When an agent changes behavior, it updates the relevant ADD **in the same PR** (spec-driven loop:
read spec → implement → update spec). The PRD remains the root; these docs are its detailed leaves.
Open questions live in PRD §12 (`OQ-*`); resolving one updates both the PRD and the affected ADD.
