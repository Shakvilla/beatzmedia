# Prompt — Generate the BeatzClik Backend PRD

> Paste everything inside the **PROMPT** block below into a Claude session that has access
> to this repository. It instructs Claude to produce a single, implementation-ready Product
> Requirements Document (PRD) for the BeatzClik **backend**, grounded in the artifacts that
> already exist here, and to express requirements at **two levels** — High-Level Functional
> Requirements (capability) and Low-Level Functional Requirements (atomic, testable).
> Everything outside the block is guidance for you, the human; only the block goes to Claude.

---

## PROMPT

You are a senior backend product engineer and technical writer. Produce a complete,
implementation-ready **Product Requirements Document (PRD)** for the **BeatzClik backend**.
This PRD is the root artifact of a **spec-driven development** process: AI coding agents will
read it in a loop (read spec → plan → implement → test → verify → update spec), so it must be
precise, unambiguous, traceable, and free of hand-wavy language.

### 1. Product context

BeatzClik is a fullstack music streaming **and** marketplace platform for the Ghanaian and
broader African market. Musicians (creators) sell and distribute music, beats, merch,
podcasts, and event tickets to fans. Domain facts you must respect:

- **Buy-to-own model**, not pure subscription streaming. Fans purchase tracks/releases and
  own them permanently. Non-owners get a time-limited preview (currently 30 seconds in the UI).
- Prices are in **Ghana Cedis (₵)**. Primary payment rails are **Mobile Money (MoMo)** — MTN
  MoMo, Telecel/Vodafone Cash, AirtelTigo Money — plus card and bank transfer.
- Creator revenue share is **70%** of each sale; the platform keeps 30%. Multi-track releases
  get an automatic bundle discount.
- Three product surfaces already exist in the frontend: the **Fan app**, the **Artist Studio**
  (creator dashboard), and the **Admin console** (platform operations).

### 2. Authoritative source material (READ THESE FIRST)

The frontend is already fully built and is the **functional specification**. Treat it and its
derived contract as the source of truth; do not invent features that contradict them.

1. **`API-CONTRACT.md`** (repo root) — the proposed REST contract derived from the UI:
   entities, endpoints, and request/response shapes. This is your primary structural input.
   Its section map (use it as the module skeleton): Auth & accounts; Catalog; Playback &
   streaming; Library & collection; Commerce; Store; Podcasts; Events & ticketing;
   Notifications; Studio (Profile, Releases, Podcasts, Insights, Payouts, Settings); Admin
   (Overview/health, Users, Catalog moderation, Moderation queue, Finance, Editorial, Trust &
   safety, Support, Compliance); Audit log; Admin RBAC & platform settings.
2. **`Frontend/src/`** — the React/TypeScript app:
   - `Frontend/src/types.ts` and `Frontend/src/lib/*-data.ts` (`mock-data.ts`,
     `studio-data.ts`, `admin-data.ts`) define the **domain model** — entities, fields, enums,
     and relationships the backend must serve.
   - `Frontend/src/features/*` (auth, cart, collection, player, notifications, studio) reveal
     **stateful behavior and business rules** (ownership, 30s preview gating, payouts, 70%
     splits, bundle discount, notifications feed).
   - `Frontend/src/routes/*` reveal **every screen and the data each one needs**.

Before writing, inventory these files and reconcile differences between the mock data, the
types, and `API-CONTRACT.md`. Where they disagree, record it in the "Open questions /
discrepancies" section with a recommended default rather than silently choosing.

### 3. Mandatory technical constraints

Write the PRD for, and only for, this stack and architecture:

- **Language/Framework:** Java with **Quarkus**. Name the concrete extensions you rely on
  (e.g. RESTEasy Reactive, Hibernate ORM with Panache, Flyway, SmallRye JWT/OIDC, Hibernate
  Validator, SmallRye Health, Micrometer/OpenTelemetry) where relevant.
- **Database:** **PostgreSQL**, with **Flyway** versioned migrations. Specify the schema
  (tables, columns, types, keys, indexes, constraints) per module.
- **Local development & infrastructure:** **Docker Compose** is the canonical local
  environment. The PRD must define the Compose topology and every service in it — at minimum
  PostgreSQL, the Quarkus app, an S3-compatible object store for media (e.g. MinIO), and a
  mail/SMS capture service for local testing (e.g. MailHog) — plus required environment
  variables, named volumes, healthchecks, seeding, and how Quarkus dev services relate to it.
  Note the path toward containerized deployment (Dockerfile / Jib, image build, config via
  env).
- **Deployment shape:** **Monolith** (single deployable), not microservices.
- **Architecture:** **Hexagonal (Ports & Adapters)**. Describe every module as:
  - **Domain** — entities, value objects, aggregates, domain services, invariants; pure and
    framework-free.
  - **Application** — use cases (input ports) and output ports (interfaces).
  - **Adapters** — **inbound** (REST resources mapping to input ports) and **outbound**
    (Postgres repositories, payment-gateway clients, object storage, email/SMS, implementing
    output ports).
  - State the dependency rule explicitly: domain depends on nothing; adapters depend on
    application/domain, never the reverse.
- Even as a monolith, organize code into **modules per bounded context** (e.g. Identity &
  Access, Catalog & Releases, Commerce/Orders & Ownership, Payments & Payouts, Royalty Splits,
  Podcasts, Events & Ticketing, Notifications, Admin/Moderation, Analytics). Define module
  boundaries and how they communicate (in-process application services / domain events)
  without leaking persistence across modules.

### 4. Functional requirements at TWO levels (this is the core deliverable)

Every capability must be expressed at both levels, with stable IDs and full traceability:

- **High-Level Functional Requirements (HLFR).** Capability-level, business-facing statements
  of what the system must do, one set per bounded context/module. Coarse-grained and stable.
  Use IDs like `HLFR-COMMERCE-01`. Each HLFR states the capability, the actor(s), the surfaces
  it serves, and its business rationale.
- **Low-Level Functional Requirements (LLFR).** The atomic, independently implementable,
  **testable** decomposition of each HLFR. Each LLFR must include: a stable ID
  (`LLFR-COMMERCE-01.3`) traceable to its parent HLFR; precise behavior; the exact endpoint(s)
  (method + path) and request/response fields lifted from `API-CONTRACT.md`/types; field-level
  validation rules; enums and **state transitions** (e.g. order `pending → paid → fulfilled →
  refunded`); authorization (role/scope required); error cases with **HTTP status codes** and
  an error model; idempotency where money or side effects are involved; the ports/adapters and
  tables it touches; and **acceptance criteria in Given/When/Then** form.
- Provide a **Requirements Traceability Matrix** mapping HLFR → LLFR → work unit → owning
  module → related `API-CONTRACT.md` endpoints, so coverage is auditable and nothing is orphaned.

### 5. Spec-driven, agent-looped delivery (design the PRD to feed this)

Structure the document so it decomposes cleanly for agents:

- Break the product into **modules → HLFRs → LLFRs → atomic work units**. Each work unit is
  small enough to implement and verify in one iteration and maps to one or more LLFRs.
- For each work unit, give explicit Given/When/Then acceptance criteria, the ports/adapters it
  touches, the data it reads/writes, and its dependencies on other units.
- Define a **sequencing / dependency graph** (identity and persistence foundations before
  commerce; payments before payouts; etc.) so agents know a valid build order.
- Specify the **definition of done** for every unit: passing unit + integration tests, contract
  conformance to `API-CONTRACT.md`, migrations included, runs under the Docker Compose stack,
  and no violation of the hexagonal dependency rule.
- Note where each section should later expand into its own detailed spec file (the PRD is the
  index/root; downstream specs are the leaves).

### 6. Required PRD structure

Produce the document with at least these sections:

1. **Overview & goals** — what the backend is, product vision recap, in-scope vs. explicitly
   out-of-scope, success criteria.
2. **Personas & surfaces** — Fan, Creator (Studio), Admin; what each needs from the backend.
3. **Domain model** — entities, value objects, aggregates, enums, relationships (ER overview)
   and the key invariants (ownership granted only on successful payment; 70/30 split; bundle
   discount; 30s preview for non-owners; scheduled releases go live at their time; refund/
   dispute rules).
4. **Architecture** — hexagonal layering, the monolith module map, the dependency rule,
   cross-module communication, and technology choices (Quarkus extensions, PostgreSQL +
   Flyway, auth mechanism, payment integration strategy, media storage/streaming).
5. **Local environment & infrastructure** — the **Docker Compose** topology and services, env
   vars, volumes, healthchecks, seeding, dev-vs-prod config, and the containerized build/
   deployment path.
6. **Bounded contexts / modules** — one subsection per module: responsibilities, **HLFRs**,
   their **LLFRs**, key use cases (input ports), output ports, inbound/outbound adapters, owned
   tables, and exposed endpoints (cross-referenced to `API-CONTRACT.md`).
7. **Functional requirements catalog & traceability matrix** — the consolidated HLFR/LLFR list
   and the HLFR → LLFR → work-unit → endpoint matrix from §4.
8. **Feature breakdown & work units** — the decomposed, agent-ready units with acceptance
   criteria and dependencies, organized for the spec-driven loop (§5).
9. **Cross-cutting concerns** — authN/authZ & roles (fan/creator/admin + admin RBAC scopes),
   payments & idempotency, money handling & rounding (cedis, minor units), media storage,
   transcoding, and **signed/expiring preview URLs** that enforce the 30s limit server-side,
   notifications (in-app/email/SMS), error model, pagination, validation, observability/
   logging/tracing, security, rate limiting, and audit logging.
10. **Non-functional requirements** — performance, scalability within a monolith, data
    integrity/consistency, availability, and compliance relevant to payments and PII.
11. **Build sequencing / roadmap** — dependency-ordered phases for agents to follow.
12. **Open questions / discrepancies** — anything underspecified by the frontend/contract, each
    with a recommended default so work isn't blocked.

### 7. Requirements to ADD beyond the current frontend

The UI runs on mock data, so some backend concerns have no UI yet. Add them as first-class,
clearly-labelled **proposed** requirements (HLFR/LLFR), each with a sensible default:

- **MoMo payment integration & reconciliation** — initiate charge, handle asynchronous
  provider **webhooks/callbacks**, idempotency keys, ret/timeout handling, and grant ownership
  only on confirmed settlement; reconciliation of provider records vs. internal ledger.
- **Money ledger & payouts** — double-entry-style ledger, creator balance accrual from the 70%
  split, withdrawal requests, payout execution to MoMo/bank, and **KYC/identity verification**
  gating payouts.
- **Refunds, chargebacks & disputes** — lifecycle, ownership revocation rules, and admin
  adjudication tied to the Admin console's dispute screens.
- **Media pipeline** — upload (multipart/resumable), virus/format validation, **audio
  transcoding** to streamable formats (e.g. HLS), artwork processing, object-storage layout,
  and secure delivery (signed URLs; full file only for owners, preview clip for others).
- **Search & discovery indexing**, **feature flags / platform settings** (already in the Admin
  UI), **scheduled jobs** (release go-live, scheduled podcast episodes, digest emails),
  **email/SMS delivery**, **rate limiting & abuse protection**, **audit logging** for admin and
  money actions, **observability** (health, metrics, tracing), and **data
  protection/compliance** appropriate to PII and the Ghana Data Protection Act.

State clearly which requirements are derived from the frontend/contract and which are proposals.

### 8. Style and quality bar

- Be specific and testable. Prefer concrete field names, enums, status values, and HTTP status
  codes (lifted from the types and `API-CONTRACT.md`) over prose generalities.
- Use prose and tables; reserve bullet lists for genuinely enumerable items. Professional and
  readable.
- Do **not** write Java implementation code in the PRD. Describe interfaces, responsibilities,
  contracts, schemas, and acceptance criteria; leave code to downstream specs and agents.
- Every HLFR and LLFR carries a stable ID and traces to its parent/children and to endpoints.
- When you must assume, state it inline and add it to Open questions. Ground every requirement
  in the source material; mark anything not in the frontend or contract as a **proposal**.

### 9. Output

First, briefly inventory what you found in the source files (1–2 short paragraphs and, if
helpful, a small reconciliation table). Then output the complete PRD as a single well-structured
Markdown document titled **`BeatzClik Backend — Product Requirements Document`**, and save it as
`BACKEND-PRD.md` in the repository root.

---

## Notes for you (not part of the prompt)

- Run the prompt in a session with read access to `API-CONTRACT.md` and `Frontend/`.
- Default scope is the **whole backend**, expressed at both HLFR and LLFR levels. To pilot one
  module first (e.g. Identity + Commerce end-to-end), say so at the top of §1.
- To have Claude confirm direction before a long write, add to §8: "Pause and ask me up to 5
  clarifying questions before writing."
- Natural follow-on artifacts after the PRD: per-module spec files (the leaves), the Flyway
  migration set, the `docker-compose.yml`, and an OpenAPI spec generated from / checked against
  `API-CONTRACT.md`.
