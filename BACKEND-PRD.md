# BeatzClik Backend — Product Requirements Document

> **Status:** Draft v1.0 · **Scope:** Whole backend (all bounded contexts), expressed at both
> High-Level (HLFR) and Low-Level (LLFR) functional-requirement levels · **Audience:** AI coding
> agents and human engineers executing a spec-driven loop (read spec → plan → implement → test →
> verify → update spec) · **Owner:** OnePayGh / BeatzClik platform team.

This PRD is the **root artifact** of BeatzClik's backend. It is grounded in the finished frontend
(`Frontend/src/`) and the reverse-engineered REST contract (`API-CONTRACT.md`), both of which are
treated as the functional specification. Where this document adds capability that the frontend does
not yet exercise (payments, ledger, media pipeline, etc.), those requirements are explicitly marked
**[PROPOSAL]**. Everything else is **[DERIVED]** from the source material.

---

## 0. Source inventory & reconciliation

### What was found

The repository contains a **fully built React/TypeScript frontend** (TanStack Router, Vite, Tailwind)
under `Frontend/src/`, a **proposed REST contract** at `API-CONTRACT.md`, and an **empty Quarkus
scaffold** under `backend/` (`groupId org.shakvilla`, `artifactId beatzmedia`, Quarkus 3.34.3, Java 25,
extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-rest-client-jackson`,
`quarkus-smallrye-openapi`, `quarkus-arc`; only a `GreetingResource` exists). The domain model is
defined authoritatively in `Frontend/src/types/index.ts` and the `Frontend/src/lib/*-data.ts`
mock-data modules (`mock-data.ts`, `studio-data.ts`, `studio-payouts.ts`, `studio-analytics.ts`,
`admin-data.ts`, `store-data.ts`, `podcast-data.ts`, `event-data.ts`, `lyrics-data.ts`). Stateful
business rules live in `Frontend/src/features/*` contexts (`auth`, `cart`, `collection`, `player`,
`notifications`, `studio/release-draft`). Every screen and its data needs are visible in
`Frontend/src/routes/*` (66 route files spanning Fan, Studio, and Admin surfaces).

The hard business constants extracted from code (these are authoritative and must be configurable,
not hard-coded, in the backend): creator revenue share **70%** (`CREATOR_REVENUE_SHARE = 0.7`),
platform fee **30%** (`platformFeePct: 30`), automatic multi-track **bundle discount 24%**
(`BUNDLE_DISCOUNT = 0.24`), non-owner preview window **30 seconds** (`PREVIEW_SECONDS = 30`), flat
cart **service fee ₵0.50** (`SERVICE_FEE = 0.5`), **tip fee 10%** (creator nets 90% of tips), minimum
payout **₵10** (`MIN_PAYOUT = 10`), withdrawal fee **bank ₵5 / MoMo 1% (min ₵1)**, payout day
**Friday**, currency **GHS** (cedis), order reference format **`BZ-YYYY-NNNNN`**, durations stored as
**whole seconds**, money stored as a structured `{ amount, currency }` value (never a display string).

### Reconciliation of discrepancies

| # | Topic | Frontend / mock | `API-CONTRACT.md` | Recommended default (this PRD) |
|---|---|---|---|---|
| R1 | Admin role naming | `'Super-admin' \| 'Finance' \| 'Moderator' \| 'Editor' \| 'Support'` (title case, `admin-data.ts`) | `super-admin \| finance \| moderator \| editor \| support` (kebab, JWT claim) | Persist canonical lowercase kebab scopes; render labels in UI. See OQ-1. |
| R2 | Tip economics | Payouts: tips net **90%** (10% fee); podcast `tip` endpoint exists | 70/30 stated generally; tip endpoint in §8 | Tips use a **10% fee** (creator 90%); sales/royalties use **30% fee** (creator 70%). OQ-2. |
| R3 | Bundle discount | `BUNDLE_DISCOUNT = 0.24` applied to album price | Not mentioned | Album list price = `round(Σ track prices × (1 − 0.24), 2)`. Configurable. |
| R4 | Ownership granularity | `ownedTracks: ID[]` only (collection) | `/me/owned` returns track ids; album purchase adds all album tracks | Ownership is recorded **per track**; album/season purchases expand to constituent track/episode grants. |
| R5 | Compliance request types | `'DSAR-export' \| 'DSAR-delete' \| 'Takedown' \| 'Tax'` | `?type=DSAR\|Takedown\|Tax` | Use the finer mock enum (split DSAR into export/delete). |
| R6 | Release status | `'live' \| 'scheduled' \| 'in_review' \| 'draft'` | same | Adopt as-is; add `takedown` for moderation parity. OQ-7. |
| R7 | User status | `'active' \| 'pending' \| 'suspended'` (admin) vs `Account.isArtist/isAdmin` (auth) | `Account { isArtist, isAdmin }` | Account carries role flags; admin lifecycle status is a separate `account_status` enum. |
| R8 | Preview enforcement | Client enforces 30s via player timer | Server returns `previewSeconds` + signed URL | Server is authoritative: signed preview URL is **clipped to 30s server-side**; client timer is advisory only. |

Open items that the source genuinely under-specifies are carried into **§12 Open questions** with a
recommended default each, so no work unit is blocked.

---

## 1. Overview & goals

### 1.1 What the backend is

The BeatzClik backend (Quarkus service `beatzmedia`) is the **single deployable monolith** that turns
the existing mock-driven frontend into a real product. It serves three surfaces — the **Fan app**, the
**Artist Studio**, and the **Admin console** — over a versioned REST API (`/v1`) whose shapes already
exist as TypeScript types the UI depends on. The frontend will replace each mock `getX()` with a
TanStack Query hook against these endpoints **without any change to the rendered UI**; therefore the
backend's contract conformance to `API-CONTRACT.md` and the domain types is a hard acceptance gate.

### 1.2 Product vision recap

BeatzClik is a music streaming **and** marketplace platform for Ghana and the broader African market.
Its defining departure from Western streaming is the **buy-to-own** model: fans purchase tracks,
releases, premium podcast episodes, beats, merch, and event tickets and own them permanently; they do
**not** rent a catalog via subscription. Non-owners get a **30-second preview** of for-sale tracks.
Prices are in **Ghana Cedis (₵)** and the primary payment rail is **Mobile Money (MoMo)** — MTN MoMo,
Telecel/Vodafone Cash, AirtelTigo Money — with card and bank transfer as secondary rails. Creators
keep **70%** of each sale (90% of tips); the platform keeps the remainder.

### 1.3 In scope

Authentication and accounts; public catalog (artists, albums, tracks, playlists, browse); playback
with server-enforced preview gating; per-user library/collection; commerce (cart, checkout, orders,
ownership grants); the store marketplace; podcasts (incl. premium/early-access and tips); events and
ticketing; notifications (in-app, with email/SMS delivery); the full Artist Studio (profile, releases
with a 4-step wizard, podcasts, insights/analytics, audience, payouts, settings); the full Admin
console (overview/health, users, catalog moderation, moderation queue, finance, editorial, trust &
safety, support, compliance); audit logging; admin RBAC and platform settings; and the proposed
backend-only concerns: **MoMo payment integration & reconciliation, a money ledger & payouts with KYC
gating, refunds/chargebacks/disputes, the media upload→transcode→signed-delivery pipeline, search
indexing, feature flags, scheduled jobs, email/SMS delivery, rate limiting, and observability.**

### 1.4 Out of scope (explicitly)

Native mobile apps; the frontend itself (already built); a recommendation/ML ranking engine (curation
is editorial + simple popularity sorts); fan-to-fan messaging (`flags.fanMessaging` ships **off**);
live/real-time concert streaming; multi-currency pricing (GHS only for v1, though `Money.currency` is
modeled to allow future expansion); blockchain/NFT ownership; and tax filing automation (the platform
generates statements and tracks compliance tasks but does not file).

### 1.5 Success criteria

The backend is successful when: (a) every endpoint in `API-CONTRACT.md` is implemented and returns
shapes that validate against the frontend types; (b) the frontend runs against it with mocks removed
and **no visual change**; (c) ownership is granted **only** on confirmed payment settlement and is
correctly enforced by the preview gate; (d) the 70/30 split and 24% bundle discount are computed
exactly and reconciled in a balanced ledger; (e) all privileged mutations are audited; (f) the full
stack boots with one `docker compose up`; and (g) every work unit ships with passing unit +
integration tests and Flyway migrations, honouring the hexagonal dependency rule.

---

## 2. Personas & surfaces

| Persona | Surface | Role / scope | What they need from the backend |
|---|---|---|---|
| **Fan** | Fan app | `fan` | Browse/search catalog; stream (full if owned/free, 30s preview otherwise); manage library (likes, follows, saved albums, playlists, owned tracks); cart → checkout via MoMo/card/bank; view orders; buy store items, premium podcast episodes, event tickets; tip creators; receive notifications; manage account settings (theme, audio, notifications, country, phone). |
| **Creator (Artist Studio)** | Artist Studio | `artist` (superset of `fan`) | Become an artist; manage public profile; create/manage releases via the 4-step wizard (details → tracks upload → splits → review) producing an `in_review` release; upload audio (WAV/FLAC); set per-track price and royalty splits with collaborator confirmation; manage podcast shows/episodes (free/premium/early-access); view insights (streams, sales, followers, tips, top tracks, countries, sources) and audience; view payout balance/ledger, manage payout methods, request withdrawals (KYC-gated); configure studio settings. |
| **Admin** | Admin console | `super-admin \| finance \| moderator \| editor \| support` | Operate the platform per scoped RBAC: overview KPIs & system health; user lookup/verify/suspend/reactivate/impersonate/data-export; catalog moderation (approve/flag/takedown); moderation queue; finance (GMV, fees, run weekly/single payouts blocked on KYC, ledger, disputes refund/reject/escalate); editorial (featured slots, push schedule, curated playlists); trust & safety (risk signals review/clear/ban); support tickets; compliance (DSAR/Takedown/Tax with due dates); audit log; team & platform settings. Every privileged action is audited. |

Role hierarchy: `artist ⊃ fan` (an artist can also act as a fan). Admin roles are **orthogonal** to
fan/artist and are enforced by scope, not inheritance (see §6.11 and §9.1).

---

## 3. Domain model

### 3.1 Aggregates and entities (overview)

The model is organized into aggregates owned by bounded contexts. An **aggregate** is the consistency
boundary for a transaction; cross-aggregate effects happen via in-process domain events (§4.4).

| Aggregate (root) | Key entities / value objects | Owning context |
|---|---|---|
| **Account** | Account, FanSettings, Credential, Session, SocialIdentity | Identity & Access |
| **Artist profile** | ArtistProfile, Show, PressAsset, SocialLinks (VO) | Identity & Access / Catalog |
| **Album** | Album, (track refs) | Catalog & Releases |
| **Track** | Track, TrackCredit (VO), Lyrics (timed lines) | Catalog & Releases |
| **Playlist** | Playlist (editorial/public), UserPlaylist (fan-owned) | Catalog / Library |
| **Release** | Release, ReleaseTrack, SplitEntry, ReleaseDraft | Catalog & Releases |
| **Collection** | Collection (likes, follows, saved, owned, user playlists) | Library & Collection |
| **Ownership grant** | OwnershipGrant (account × track/episode, source order) | Commerce / Ownership |
| **Cart** | Cart, CartItem (VO) | Commerce |
| **Order** | Order, OrderLine (VO), OrderSnapshot | Commerce |
| **Payment** | PaymentIntent, PaymentEvent (webhook), ProviderRef | Payments & Payouts |
| **Ledger** | LedgerAccount, LedgerEntry (double-entry), CreatorBalance | Payments & Payouts |
| **Payout** | PayoutMethod, WithdrawalRequest, PayoutBatch, PayoutTxn, KycRecord | Payments & Payouts |
| **StoreItem** | StoreItem, LicenseOption (VO), MerchVariant (VO) | Store |
| **Podcast** | Podcast, PodcastEpisode | Podcasts |
| **Event** | Event, TicketTier (VO), Ticket | Events & Ticketing |
| **Notification** | AppNotification, DeliveryAttempt | Notifications |
| **Dispute** | Dispute, DisputeEvent, Refund | Payments & Payouts / Admin |
| **Moderation** | ModerationCase, RiskSignal, SupportTicket, ComplianceRequest | Admin / Moderation |
| **Audit** | AuditEntry | Audit (cross-cutting) |
| **Platform config** | PlatformSettings, FeatureFlag, AdminMember | Admin / Platform |
| **Analytics** | PlayEvent, SalesRollup, AudienceRollup | Analytics |

### 3.2 Core enums (lifted from types)

`OwnershipStatus = owned | free | for-sale` · `Genre = Afrobeats | Hiplife | Highlife | Amapiano |
Drill | Gospel | R&B | Reggae | Jazz` · `Money.currency = GHS` · `StoreItemType = TRACK | ALBUM |
BEAT_LICENSE | MERCH | EXCLUSIVE` · `LicenseTier = LEASE | PREMIUM | EXCLUSIVE` · `StoreSort =
popular | newest | price-asc | price-desc` · `CartItemKind = track | album | album-rest | store |
episode | season-pass | ticket` · `ReleaseType = single | ep | album | mixtape` · `ReleaseStatus =
live | scheduled | in_review | draft (+ takedown)` · `SplitConfirmation = self | confirmed | pending
| auto` · `EpisodeStatus = published | scheduled | draft` · `PodcastCategory = News & Politics |
Comedy | Business | Sports | Culture | Tech | Health | Storytelling` · `EventStatus = on-sale |
selling-fast | sold-out` · `EventCategory = Concert | Festival | Club Night | Listening Party | Tour`
· `PayoutType = Sale | Royalty | Tip | Cash-out` · `PayoutStatus = cleared | paid | pending` ·
`MethodKind = momo | bank` · `NotificationType = sale | tip | follower | payout | release | system` ·
`AdminRole = super-admin | finance | moderator | editor | support` · `UserStatus = active | pending |
suspended` · `ComplianceType = DSAR-export | DSAR-delete | Takedown | Tax` · `RiskLevel = high | med
| low` · `RiskStatus = open | cleared | banned` · `TicketStatus = open | pending | resolved` ·
`AuditType = user | catalog | finance | moderation | settings | editorial`.

### 3.3 Key invariants (enforced by the domain, not the UI)

1. **Ownership-on-payment.** An `OwnershipGrant` is created **only** when a `PaymentIntent` reaches
   `SETTLED`. No grant on `PENDING`/`FAILED`. (INV-1)
2. **Album/season expansion.** Purchasing an `album`/`album-rest` grants all constituent tracks;
   purchasing a `season-pass` grants all premium episodes of the show. (INV-2)
3. **Preview gate.** For a `for-sale` track the caller does not own, `/stream` returns a signed URL
   clipped to `previewSeconds = 30`; full audio only for `owned`/`free`. (INV-3)
4. **Revenue split.** Every settled sale/royalty credits the creator `70%` and the platform `30%`;
   tips credit creator `90%` / platform `10%`. Percentages come from `PlatformSettings`, not code. (INV-4)
5. **Bundle discount.** A multi-track release's list price = `round(Σ track prices × (1 − bundle), 2)`
   with `bundle = 0.24`. Singles get no discount. (INV-5)
6. **Ledger balance.** Every monetary movement posts **balanced** double-entry rows (Σ debits =
   Σ credits); creator withdrawable balance = cleared credits − cleared cash-outs. (INV-6)
7. **Scheduled go-live.** A `scheduled` release/episode becomes `live`/`published` at its scheduled
   instant via a job; it is not publicly streamable before then. (INV-7)
8. **Withdrawal floor & KYC.** A withdrawal requires `amount ≥ MIN_PAYOUT (₵10)`, sufficient cleared
   balance, and a `KycRecord` in `verified` state. (INV-8)
9. **Refund revokes ownership.** A completed refund revokes the corresponding `OwnershipGrant`(s) and
   reverses the ledger entries; the creator's accrued credit is clawed back. (INV-9)
10. **Audit completeness.** Every privileged mutation (suspend, verify, takedown, payout, refund,
    settings change, role change, impersonate) appends exactly one `AuditEntry`. (INV-10)
11. **Money precision.** Money is stored in **minor units (pesewas, integer)** internally; the API
    surface uses `{ amount: decimal cedis, currency }`. Rounding is half-up to 2 dp at boundaries. (INV-11)
12. **Split sum.** Per-track royalty splits sum to **≤ 100%** of the creator pool; the originating
    creator implicitly holds the remainder; a release cannot go `live` with `pending` splits. (INV-12)

### 3.4 Entity-relationship overview (textual)

`Account 1—1 FanSettings`; `Account 1—0..1 ArtistProfile`; `Account 1—1 Collection`; `Account 1—*
OwnershipGrant`; `Account 1—1 Cart`; `Account 1—* Order`; `Order 1—* OrderLine`; `Order 1—1
PaymentIntent`; `Order 1—* OwnershipGrant (on settle)`; `ArtistProfile 1—* Release`; `Release 1—*
Track`; `Track *—1 Album (optional)`; `Track 1—* SplitEntry`; `Album *—1 ArtistProfile`; `Track 1—0..1
Lyrics`; `Account *—* Track (likes, owned)`; `Account *—* ArtistProfile (follows)`; `Podcast 1—*
PodcastEpisode`; `Event 1—* TicketTier`; `Order/Ticket 1—1 Event`; `Creator 1—* PayoutMethod`;
`Creator 1—* WithdrawalRequest`; `LedgerAccount 1—* LedgerEntry`; `Dispute *—1 Order`; `AuditEntry
*—1 Account (actor)`.

---

## 4. Architecture

### 4.1 Hexagonal (Ports & Adapters) layering

Every module is structured in three concentric layers with a strict dependency rule:

- **Domain** — entities, value objects, aggregates, domain services, and invariants. Pure Java, **no
  framework imports** (no Jakarta, no Quarkus, no Hibernate annotations on domain types — persistence
  uses separate JPA entities or mapped records). Domain depends on **nothing**.
- **Application** — use cases as **input ports** (interfaces invoked by inbound adapters) and **output
  ports** (interfaces the domain/application needs the outside world to fulfil: repositories, payment
  gateway, object storage, mailer/SMS, clock, id generator). Application depends only on Domain.
- **Adapters** —
  - **Inbound:** Quarkus REST resources (`quarkus-rest` / RESTEasy Reactive) that map HTTP to input
    ports, plus scheduled-job triggers and webhook receivers.
  - **Outbound:** PostgreSQL repositories (Hibernate ORM with Panache), payment-gateway clients
    (MoMo provider REST clients via `quarkus-rest-client`), object storage (S3/MinIO), mailer/SMS,
    and event publishers — each **implementing** an output port.

**Dependency rule (explicit):** `adapters → application → domain`. Domain never imports application or
adapters; application never imports adapters. Inbound and outbound adapters never import each other.
Violations fail the build (enforced by ArchUnit tests; see DoD in §8).

### 4.2 Monolith module map (bounded contexts)

Single deployable, internally partitioned into modules per bounded context. Suggested package root
`org.shakvilla.beatzmedia.<context>` with `domain` / `application` / `adapter.in.rest` /
`adapter.out.persistence` (etc.) subpackages.

| Module | Bounded context | Primary responsibility |
|---|---|---|
| `identity` | Identity & Access | Accounts, auth, sessions, fan settings, artist upgrade, admin members/RBAC |
| `catalog` | Catalog & Releases | Artists, albums, tracks, playlists, lyrics, browse/home feed, releases + wizard |
| `playback` | Playback & Streaming | Stream URL issuance (preview vs full), play recording |
| `library` | Library & Collection | Likes, follows, saved albums, user playlists, owned-track reads |
| `commerce` | Commerce / Orders & Ownership | Cart, checkout orchestration, orders, ownership grants |
| `payments` | Payments & Payouts | Payment intents, MoMo/card/bank gateways, reconciliation, ledger, payouts, KYC, refunds/disputes |
| `store` | Store | Marketplace catalog (beats/hi-fi/merch/exclusives), license tiers, variants |
| `podcasts` | Podcasts | Shows, episodes, premium/early-access, tips |
| `events` | Events & Ticketing | Events, ticket tiers, ticket issuance |
| `notifications` | Notifications | In-app feed + email/SMS delivery |
| `studio` | Studio (creator) | Studio aggregation over catalog/payments/analytics; profile, settings |
| `admin` | Admin / Moderation | Overview, users, moderation, finance ops, editorial, risk, support, compliance, settings |
| `analytics` | Analytics | Play/sales/audience rollups feeding Studio insights & Admin overview |
| `audit` | Audit (cross-cutting) | Append-only audit trail |
| `media` | Media pipeline (shared infra) | Upload, validation, transcode, artwork, signed delivery |
| `platform` | Cross-cutting kernel | Money VO, error model, pagination, clock, ids, feature flags, config |

### 4.3 Technology choices

- **Framework:** Quarkus 3.34.x on Java 25 (per existing scaffold). Extensions: `quarkus-rest` +
  `quarkus-rest-jackson` (inbound REST/JSON), `quarkus-rest-client-jackson` (MoMo/provider clients),
  `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` (persistence), `quarkus-flyway`
  (migrations), `quarkus-hibernate-validator` (field validation), `quarkus-smallrye-jwt`
  (+`quarkus-smallrye-jwt-build`) or `quarkus-oidc` for auth (see OQ-3), `quarkus-smallrye-openapi`
  (contract publishing), `quarkus-smallrye-health`, `quarkus-micrometer-registry-prometheus` +
  `quarkus-opentelemetry` (observability), `quarkus-scheduler` (jobs), `quarkus-mailer` (email),
  `quarkus-amazon-s3` or `quarkus-minio` client (object storage), `quarkus-redis-client` (rate-limit
  buckets / caching, optional), `quarkus-messaging-*` only if events go out-of-process (default:
  in-process CDI events).
- **Database:** PostgreSQL 16, schema-per-nothing (single schema), **Flyway** versioned migrations
  `V<n>__<desc>.sql`, one migration set per module contribution. Money in **integer minor units**.
- **Auth mechanism:** Stateless **Bearer JWT** (`Authorization: Bearer <jwt>`) carrying `sub`
  (account id) and `roles` (`fan`/`artist` + admin scopes). Access token short-lived; refresh via
  re-login for v1 (refresh tokens = OQ-3). Password hashing with Argon2id.
- **Payment integration strategy:** an outbound `PaymentGateway` port with provider adapters (MTN
  MoMo, Telecel/Vodafone Cash, AirtelTigo Money, card, bank). Charges are **asynchronous**: initiate →
  provider callback/webhook → settle. **Idempotency keys** on every money mutation; ownership granted
  only on settlement; a reconciliation job compares provider records to the internal ledger.
- **Media storage/streaming:** S3-compatible object store (MinIO locally). Originals in a private
  bucket; transcoded HLS renditions in a delivery bucket fronted by signed, time-boxed URLs. The
  preview rendition is a **30s server-clipped** asset; full renditions are served only to owners.

### 4.4 Cross-module communication

Modules communicate **in-process** through (a) **application services** exposed as input ports
(synchronous, e.g. commerce calls payments' `InitiateChargeUseCase`) and (b) **domain events**
published via CDI (`jakarta.enterprise.event.Event`) and consumed by `@Observes` handlers
(asynchronous side effects, e.g. `PaymentSettled` → commerce grants ownership → notifications notifies
the creator → analytics rolls up the sale). **Persistence is never shared across modules**: a module
reads/writes only its own tables; cross-context data is obtained by calling the owning module's port
or by carrying ids/denormalized snapshots in events. Key events: `AccountRegistered`,
`ArtistUpgraded`, `PaymentSettled`, `PaymentFailed`, `OwnershipGranted`, `OrderRefunded`,
`ReleaseApproved`, `ReleaseWentLive`, `EpisodePublished`, `TipReceived`, `WithdrawalRequested`,
`PayoutSent`, `DisputeOpened`, `ContentTakenDown`, `UserSuspended`.

---

## 5. Local environment & infrastructure

### 5.1 Docker Compose topology (canonical local environment)

`docker-compose.yml` (repo root or `backend/`) defines the full stack so the system boots with one
`docker compose up`. Services:

| Service | Image (suggested) | Purpose | Ports | Healthcheck |
|---|---|---|---|---|
| `db` | `postgres:16` | Primary datastore | `5432` | `pg_isready -U beatz` |
| `app` | built from `backend/src/main/docker/Dockerfile.jvm` | Quarkus `beatzmedia` | `8080` | `GET /q/health/ready` |
| `objectstore` | `minio/minio` | S3-compatible media store (originals + delivery buckets) | `9000`/`9001` | `mc ready` / `/minio/health/live` |
| `createbuckets` | `minio/mc` (init job) | Creates `beatz-media-originals`, `beatz-media-delivery` buckets & policies | — | one-shot |
| `mail` | `axllent/mailpit` (or MailHog) | Captures outbound email locally | `1025` SMTP / `8025` UI | HTTP `8025` |
| `sms` | local stub / Mailpit-style capture (see OQ-9) | Captures outbound SMS locally | `8026` | HTTP |
| `transcoder` | `jrottenberg/ffmpeg` worker (or in-app `ffmpeg`) | Audio→HLS transcode + 30s preview clip | — | n/a |
| `cache` (optional) | `redis:7` | Rate-limit buckets / ephemeral cache | `6379` | `redis-cli ping` |

`db`, `objectstore`, and `mail` start before `app` (Compose `depends_on` with `condition:
service_healthy`). Named volumes persist data: `pgdata`, `miniodata`.

### 5.2 Environment variables (canonical set)

`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`,
`QUARKUS_FLYWAY_MIGRATE_AT_START=true`; `BEATZ_S3_ENDPOINT`, `BEATZ_S3_ACCESS_KEY`,
`BEATZ_S3_SECRET_KEY`, `BEATZ_S3_BUCKET_ORIGINALS`, `BEATZ_S3_BUCKET_DELIVERY`,
`BEATZ_SIGNED_URL_TTL_SECONDS`; `BEATZ_PREVIEW_SECONDS=30`; `BEATZ_JWT_PRIVATE_KEY` /
`BEATZ_JWT_PUBLIC_KEY` (or OIDC issuer vars); `QUARKUS_MAILER_*`; `BEATZ_SMS_*`; payment provider
secrets `BEATZ_MOMO_MTN_*`, `BEATZ_MOMO_VODAFONE_*`, `BEATZ_MOMO_AIRTELTIGO_*`, `BEATZ_CARD_*`,
`BEATZ_PAYMENT_WEBHOOK_SECRET`; `BEATZ_PLATFORM_FEE_PCT=30`, `BEATZ_CREATOR_SHARE_PCT=70`,
`BEATZ_TIP_FEE_PCT=10`, `BEATZ_BUNDLE_DISCOUNT_PCT=24`, `BEATZ_SERVICE_FEE=0.50`,
`BEATZ_MIN_PAYOUT=10`, `BEATZ_PAYOUT_DAY=FRIDAY` (these seed `PlatformSettings`, which is the runtime
source of truth). Sensible non-secret defaults live in `application.properties`; secrets come from the
environment.

### 5.3 Quarkus dev services relationship

In `quarkus:dev`, Quarkus **Dev Services** can auto-provision Postgres (Testcontainers) and MinIO when
the corresponding config is absent — useful for fast inner-loop and tests. Compose is the **canonical,
reproducible** environment (and what CI/integration tests target via `%test`/`%docker` profiles);
when Compose-provided URLs are set, Dev Services stand down. Integration tests (`*IT`) run against the
Compose Postgres + MinIO; unit tests use in-memory/fakes for output ports.

### 5.4 Seeding

A Flyway **repeatable** seed migration (`R__seed_dev_data.sql`, dev/test profiles only) loads the
mock catalog currently in `Frontend/src/lib/*-data.ts` (artists, albums, tracks, playlists, store
items, podcasts, events, a sample creator with releases/payouts, an admin team, platform settings) so
the API returns the same data the UI was built against. Seed audio is a small placeholder asset
uploaded to MinIO by the `createbuckets`/seed init.

### 5.5 Dev-vs-prod config & containerized deployment path

Quarkus config profiles `%dev`, `%test`, `%prod` separate local from production (e.g. `%prod` requires
real provider secrets, disables seed, enforces TLS for outbound). Production image is built from the
existing `backend/src/main/docker/Dockerfile.jvm` (or **Jib** for reproducible layered images / a
native image via `Dockerfile.native` for smaller footprint). Deployment is a **single container** plus
managed Postgres and S3; config is entirely via environment variables; `quarkus.flyway.migrate-at-start`
applies migrations on boot; `/q/health/live` and `/q/health/ready` back orchestrator probes.

---

## 6. Bounded contexts / modules

> **Reading guide.** Each module lists its **HLFRs** (capability-level, stable) and their **LLFRs**
> (atomic, testable). LLFR IDs nest under their parent HLFR (`LLFR-<CTX>-<n>.<m>`). Endpoints, fields,
> enums, status codes, and tables are lifted from `API-CONTRACT.md` and the frontend types. Acceptance
> criteria are in Given/When/Then form. The standard error envelope is
> `{ "error": { "code", "message", "field?" } }`; standard codes 400/401/403/404/409/422/429/500 apply
> everywhere and are only called out when a non-obvious case exists. All list endpoints accept
> `?page=&size=` and return `{ items, page, size, total }`.

### 6.1 Identity & Access (`identity`)

**Responsibilities:** account registration/login, social login, sessions/JWT, current-session reads,
artist upgrade, fan settings, password reset, admin members & RBAC, account lifecycle status.
**Owned tables:** `account`, `credential`, `social_identity`, `fan_settings`, `session` (or none if
fully stateless), `admin_member`, `password_reset_token`. **Input ports:** `RegisterFan`, `Login`,
`SocialLogin`, `Logout`, `GetCurrentAccount`, `UpgradeToArtist`, `UpdateFanSettings`, `RequestPasswordReset`,
`InviteAdmin`, `ChangeAdminRole`, `RemoveAdmin`, `ListAdminTeam`. **Output ports:** `AccountRepository`,
`CredentialHasher`, `TokenIssuer`, `SocialVerifier`, `Mailer`, `Clock`, `IdGenerator`.

**HLFR-IDENTITY-01 — Account registration & authentication.** The system must let a visitor create a
fan account and authenticate (password or social) to obtain a bearer token. *Actors:* Fan. *Surfaces:*
Fan app (login/signup). *Rationale:* gate for all per-user state.

- **LLFR-IDENTITY-01.1 — Sign up (fan).** `POST /v1/auth/signup` body `{ name, email, password }` →
  `201 { token, account }`. Validation: `name` 1–80 chars; `email` RFC-5322 + unique (409
  `EMAIL_TAKEN`); `password` ≥ 8 chars (422 `WEAK_PASSWORD`). Creates account with `isArtist=false`,
  `isAdmin=false`, `status=active`; hashes password Argon2id; issues JWT (`sub`, `roles=[fan]`).
  Emits `AccountRegistered`. *AC:* **Given** a unique email **When** signup with valid body **Then**
  201 with a usable token and `account.isArtist=false`. **Given** an existing email **Then** 409
  `EMAIL_TAKEN` and no account created.
- **LLFR-IDENTITY-01.2 — Log in.** `POST /v1/auth/login` `{ email, password }` → `200 { token,
  account }`; invalid credentials → 401 `INVALID_CREDENTIALS` (generic, no user enumeration);
  suspended account → 403 `ACCOUNT_SUSPENDED`. *AC:* **Given** valid credentials for an active account
  **When** login **Then** 200 with token whose `roles` reflect `isArtist`/admin role.
- **LLFR-IDENTITY-01.3 — Social login.** `POST /v1/auth/social` `{ provider: facebook|google|twitter,
  token }` → `200 { token, account }`. Verifies provider token via `SocialVerifier`; links or creates
  account by verified email; invalid provider token → 401 `SOCIAL_TOKEN_INVALID`. *AC:* **Given** a
  valid Google token for a new email **Then** a fan account is created and linked.
- **LLFR-IDENTITY-01.4 — Logout.** `POST /v1/auth/logout` → `204`. Revokes refresh/session if
  stateful; no-op for pure stateless JWT (client discards). Idempotent.
- **LLFR-IDENTITY-01.5 — Password reset request.** `POST /v1/me/password/reset` `{ email }` → `204`
  **always** (no enumeration); if the email exists, emails a single-use, time-boxed token via
  `Mailer`. *AC:* **Given** any email **Then** 204; **and** an existing email receives a reset link.

**HLFR-IDENTITY-02 — Current session & profile self-service.** The system must expose the
authenticated account and let it manage its own settings and role. *Actors:* Fan, Creator. *Surfaces:*
header account menu, fan settings.

- **LLFR-IDENTITY-02.1 — Get current account.** `GET /v1/me` → `200 Account { id, name, email,
  avatar?, isArtist, isAdmin }`; missing/expired token → 401. Auth: any role.
- **LLFR-IDENTITY-02.2 — Become an artist.** `POST /v1/me/become-artist` → `200 Account` with
  `isArtist=true`; creates an empty `ArtistProfile`; idempotent if already an artist; blocked when
  `flags.artistSignups=false` → 403 `FEATURE_DISABLED`. Emits `ArtistUpgraded`. Auth: fan. *AC:*
  **Given** a fan and `artistSignups=true` **When** become-artist **Then** subsequent `/me` shows
  `isArtist=true` and `/studio/*` is authorized.
- **LLFR-IDENTITY-02.3 — Update fan settings.** `PATCH /v1/me/settings` partial `FanSettings { theme,
  audioQuality, notifications{...}, country, phone }` → `200 FanSettings`. Validates `country` (ISO),
  `phone` (E.164 / Ghana format). Auth: fan.

**HLFR-IDENTITY-03 — Admin team & RBAC.** The system must let a super-admin manage admin members and
enforce role scopes server-side. *Actors:* Admin (super-admin). *Surfaces:* `/admin/settings`.

- **LLFR-IDENTITY-03.1 — List team.** `GET /v1/admin/team` → `AdminMember[] { id, name, email, role,
  lastActive }`. Auth: any admin (read). 
- **LLFR-IDENTITY-03.2 — Invite admin.** `POST /v1/admin/team/invite` `{ email, role }` →
  `201 AdminMember`; `role ∈ {super-admin,finance,moderator,editor,support}` (422 `INVALID_ROLE`);
  audited (INV-10). Auth: super-admin only (403 otherwise).
- **LLFR-IDENTITY-03.3 — Change role / remove.** `PATCH /v1/admin/team/:id` `{ role }` ·
  `DELETE /v1/admin/team/:id` → `204`. Cannot remove the last super-admin (409 `LAST_SUPER_ADMIN`).
  Audited. Auth: super-admin. *AC:* **Given** one remaining super-admin **When** delete that member
  **Then** 409 and the member is retained.

**Schema (identity, illustrative):** `account(id PK, name, email UNIQUE, avatar, is_artist bool,
is_admin bool, status enum, created_at, updated_at)`; `credential(account_id FK, password_hash,
algo)`; `social_identity(id PK, account_id FK, provider, provider_uid, UNIQUE(provider,provider_uid))`;
`fan_settings(account_id PK/FK, theme, audio_quality, notif_json jsonb, country, phone)`;
`admin_member(id PK, account_id FK, role, last_active_at)`; `password_reset_token(token PK,
account_id FK, expires_at, used bool)`. Indexes on `account.email`, `social_identity(provider,
provider_uid)`.

### 6.2 Catalog & Releases (`catalog`)

**Responsibilities:** public read catalog (home feed, search, browse, artists, albums, tracks,
playlists, lyrics) **and** the creator-owned release lifecycle (4-step wizard, moderation states,
scheduled go-live). **Owned tables:** `artist_profile`, `album`, `track`, `track_credit`, `lyrics`,
`lyric_line`, `playlist`, `playlist_track`, `browse_category`, `release`, `release_track`,
`split_entry`, `release_draft`. **Input ports:** `GetHomeFeed`, `Search`, `ListBrowseCategories`,
`GetArtist`(+tracks/albums/shows), `GetAlbum`, `GetTrack`, `GetLyrics`, `GetPlaylist`,
`ListStudioReleases`, `SubmitRelease`(wizard), `GetRelease`, `UpdateRelease`, `DeleteRelease`,
`UploadReleaseTrack`, `PublishRelease`. **Output ports:** `CatalogRepository`, `SearchIndex`,
`MediaService` (upload/transcode), `Clock`, `EventPublisher`.

**HLFR-CATALOG-01 — Public catalog browse.** The system must serve the curated home feed, search,
browse tiles, and entity detail pages exactly as the UI consumes them. *Actors:* Fan (anonymous OK
for reads). *Surfaces:* home, search, artist, album, track, playlist.

- **LLFR-CATALOG-01.1 — Home feed.** `GET /v1/home` → `{ trending: Track[], top10: Track[],
  featuredAlbums: Album[], rails: {...} }`. Reflects editorial featured slots (§6.12) + popularity.
  Public. *AC:* **Given** seeded catalog **Then** all four sections are non-empty and each `Track`
  matches the `Track` type.
- **LLFR-CATALOG-01.2 — Search.** `GET /v1/search?q=` → `{ tracks, artists, albums, playlists }` (+
  top result). `q` min length 1; empty → 422 `MISSING_QUERY`. Public. Backed by `SearchIndex` (§6.13).
- **LLFR-CATALOG-01.3 — Browse categories.** `GET /v1/browse-categories` → `BrowseCategory[] { id,
  title, colorClass }`. Public.
- **LLFR-CATALOG-01.4 — Artist profile & sub-collections.** `GET /v1/artists/:id` → `Artist`;
  `/artists/:id/tracks` → `Track[]`; `/artists/:id/albums` → `Album[]`; `/artists/:id/shows` →
  `Show[]`. Unknown id → 404 `ARTIST_NOT_FOUND`. Public.
- **LLFR-CATALOG-01.5 — Album detail.** `GET /v1/albums/:id?tracks=true` → `Album (+ tracks: Track[])`.
  Each embedded track carries per-caller `ownership`/`price` (delegated to library/commerce). 404 if
  unknown. Public.
- **LLFR-CATALOG-01.6 — Track detail & lyrics.** `GET /v1/tracks/:id` → `Track` (credits, quality);
  `GET /v1/tracks/:id/lyrics` → `{ lines: { time, text }[] }` (time in seconds); no lyrics → 404
  `LYRICS_NOT_FOUND`. Public.
- **LLFR-CATALOG-01.7 — Playlist detail.** `GET /v1/playlists/:id` → `Playlist (+ tracks: Track[])`.
  Private playlist accessed by non-owner → 404 (hide existence). Public for public playlists.

**HLFR-CATALOG-02 — Release creation & lifecycle (creator).** The system must let an artist submit a
complete release via the wizard, manage it, upload audio, and move it through `draft → in_review →
scheduled/live` (and admin `takedown`). *Actors:* Creator; Admin (moderation). *Surfaces:* Studio
releases + 4-step wizard. *Rationale:* this is how catalog inventory is created.

- **LLFR-CATALOG-02.1 — List releases.** `GET /v1/studio/releases?status=` → `StudioRelease[] { id,
  title, type, status, date, trackCount, streams, revenue, price }`. Auth: artist; only the caller's
  releases. `status ∈ {live,scheduled,in_review,draft,takedown}`.
- **LLFR-CATALOG-02.2 — Submit release (wizard).** `POST /v1/studio/releases` body = full draft
  (details `{ title, type, date?, visibility }` + `tracks: UploadedTrack[]` + `splits:
  Record<trackId, SplitEntry[]>` + per-track `price`) → `201 StudioRelease` with `status=in_review`.
  Validation: `type ∈ {single,ep,album,mixtape}`; singles have exactly 1 track, multi-track types ≥ 1
  (422 `TRACK_COUNT_INVALID`); each `price ∈ PRICE_OPTIONS {2, 2.5, 3, 0}` or arbitrary ≥ 0 (see
  OQ-5); per-track splits sum ≤ 100 (422 `SPLIT_OVER_100`, INV-12); album list price computed via
  INV-5. Emits nothing public yet (awaits approval). Auth: artist. *AC:* **Given** a valid multi-track
  draft **When** submit **Then** a release is created `in_review`, album price = `round(Σprice×0.76,2)`,
  and it is **not** returned by public catalog reads.
- **LLFR-CATALOG-02.3 — Manage release.** `GET/PATCH/DELETE /v1/studio/releases/:id`. PATCH edits
  metadata and can `publish`/`unpublish` (live↔scheduled) within allowed transitions; DELETE allowed
  only for `draft`/`in_review` (409 `RELEASE_LIVE` otherwise). Auth: owning artist.
- **LLFR-CATALOG-02.4 — Upload release track.** `POST /v1/studio/releases/:id/tracks` multipart audio
  (WAV/FLAC) → `201 UploadedTrack { id, name, duration, status: uploading|ready|error }`. Delegates to
  `MediaService` (validate format/virus, probe duration, transcode → HLS + 30s preview). Rejects
  non-WAV/FLAC → 422 `UNSUPPORTED_FORMAT`; oversize → 413. Auth: owning artist. *AC:* **Given** a WAV
  upload **Then** the track returns with a probed `duration` and transitions to `ready` once
  transcoding completes (INV-7-adjacent).
- **LLFR-CATALOG-02.5 — Release state machine.** Enforce transitions: `draft → in_review`
  (submit/edit), `in_review → scheduled` (admin approve + future date), `in_review → live` (admin
  approve + immediate), `scheduled → live` (scheduler at `date`, INV-7), `live → takedown` (admin),
  `takedown → live` (admin reinstate). Illegal transition → 409 `ILLEGAL_TRANSITION`. Emits
  `ReleaseApproved`, `ReleaseWentLive`, `ContentTakenDown`. *AC:* **Given** a `scheduled` release with
  `date` now passed **When** the go-live job runs **Then** status becomes `live` and the tracks become
  publicly streamable.

**Schema (catalog, illustrative):** `track(id PK, title, artist_id FK, album_id FK?, duration_sec int,
image, ownership enum, price_minor int?, plays bigint, quality, year, release_id FK?, status)`;
`album(id PK, title, artist_id FK, year, cover_image)`; `release(id PK, artist_id FK, title, type,
status, scheduled_at, list_price_minor, created_at)`; `release_track(release_id FK, track_id FK,
position, price_minor)`; `split_entry(id PK, track_id FK, collaborator, role, percent, confirmation)`;
`lyric_line(track_id FK, t_sec int, text)`; `playlist(id PK, title, creator, is_public)`. Full-text
indexes feed the search index (§6.13).

### 6.3 Playback & Streaming (`playback`)

**Responsibilities:** issue signed, time-boxed audio URLs deciding preview-vs-full by ownership, and
record plays. **Owned tables:** `play_event` (write-optimized; rolled up by analytics). **Input
ports:** `GetStreamUrl`, `RecordPlay`. **Output ports:** `MediaService` (signed URLs), `OwnershipReader`
(from commerce/library), `CatalogReader`, `Clock`, `EventPublisher`.

**HLFR-PLAYBACK-01 — Ownership-aware streaming.** The system must return a full audio URL for
owned/free tracks and a 30-second preview URL for for-sale tracks the caller doesn't own, enforced
server-side. *Actors:* Fan. *Surfaces:* global player, 30s preview gate. *Rationale:* protects the
buy-to-own model (INV-3).

- **LLFR-PLAYBACK-01.1 — Get stream URL.** `GET /v1/tracks/:id/stream` → `{ audioUrl,
  previewSeconds?, expiresAt }`. Logic: resolve `ownership`; if `free` or owned → full HLS signed URL,
  no `previewSeconds`; if `for-sale` and not owned → **preview** signed URL (points at the 30s clipped
  rendition) + `previewSeconds=30`. `expiresAt` = now + `BEATZ_SIGNED_URL_TTL_SECONDS`. Unknown track
  → 404. Auth: optional (anonymous gets preview for for-sale, full for free). *AC:* **Given** a
  for-sale track the caller does not own **When** GET stream **Then** the URL serves at most 30s and
  `previewSeconds=30`. **Given** the caller owns it **Then** full URL and no `previewSeconds`.
- **LLFR-PLAYBACK-01.2 — Record a play.** `POST /v1/tracks/:id/play` → `204`. Appends a `play_event`
  (account?, track, ts, full-vs-preview) for plays count & royalties; rate-limited per (account,track)
  to prevent inflation (§9). Emits `PlayRecorded`. Auth: optional. *AC:* **Given** rapid repeated
  calls **Then** only de-duped/valid plays increment the counter (anti-bot, §6.13/§9).

### 6.4 Library & Collection (`library`)

**Responsibilities:** per-user likes, follows (artists, playlists, shows), saved albums, user-created
playlists, and the owned-track read that drives playback unlock. **Owned tables:** `liked_track`,
`followed_artist`, `followed_playlist`, `followed_show`, `saved_album`, `user_playlist`,
`user_playlist_track`. (Ownership is sourced from commerce; library exposes a read.) **Input ports:**
`GetCollection`, `ToggleLike`, `ToggleFollow`, `ToggleSave`, `CreateUserPlaylist`, `RenameUserPlaylist`,
`DeleteUserPlaylist`, `AddTrackToPlaylist`, `RemoveTrackFromPlaylist`, `GetOwnedTrackIds`. **Output
ports:** `CollectionRepository`, `OwnershipReader`, `CatalogReader`.

**HLFR-LIBRARY-01 — Personal collection management.** The system must let a fan curate likes, follows,
saved albums, and personal playlists, and read their owned tracks. *Actors:* Fan. *Surfaces:* Library,
like/follow/save buttons, playlist editor. *Rationale:* backs the client `collection` store.

- **LLFR-LIBRARY-01.1 — Get collection.** `GET /v1/me/collection` → `Collection { likedTracks[],
  followedArtists[], followedPlaylists[], followedShows[], savedAlbums[], ownedTracks[],
  userPlaylists: UserPlaylist[] }`. Auth: fan.
- **LLFR-LIBRARY-01.2 — Like / unlike.** `PUT/DELETE /v1/me/likes/tracks/:id` → `204`. Idempotent
  (PUT twice = liked once). Unknown track → 404. Auth: fan.
- **LLFR-LIBRARY-01.3 — Follow / unfollow.** `PUT/DELETE /v1/me/follows/artists/:id` (and
  `/follows/playlists/:id`, `/follows/shows/:id`) → `204`. Idempotent. Auth: fan.
- **LLFR-LIBRARY-01.4 — Save / unsave album.** `PUT/DELETE /v1/me/saved/albums/:id` → `204`.
  Idempotent. Auth: fan.
- **LLFR-LIBRARY-01.5 — User playlists CRUD.** `GET/POST /v1/me/playlists` (`{ title, firstTrackId? }`
  → `201 UserPlaylist`); `PATCH/DELETE /v1/me/playlists/:id` (rename/delete);
  `PUT/DELETE /v1/me/playlists/:id/tracks/:trackId` (add/remove). `title` 1–100 chars. Non-owner
  access → 404. Auth: fan. *AC:* **Given** a fan **When** create then add two tracks **Then**
  `GET` returns the playlist with both `trackIds` in order.
- **LLFR-LIBRARY-01.6 — Owned tracks read.** `GET /v1/me/owned` → `ID[]`. Sourced from
  `OwnershipReader` (commerce grants); drives the playback unlock and `ownership` decoration. Auth: fan.

### 6.5 Commerce / Orders & Ownership (`commerce`)

**Responsibilities:** cart management, checkout orchestration (calls payments), order history, and the
creation/revocation of **ownership grants**. **Owned tables:** `cart`, `cart_item`, `order`,
`order_line`, `ownership_grant`. **Input ports:** `GetCart`, `AddCartItem`, `UpdateCartItem`,
`RemoveCartItem`, `Checkout`, `ListOrders`, `GrantOwnership` (internal, on settle), `RevokeOwnership`
(internal, on refund). **Output ports:** `CartRepository`, `OrderRepository`, `OwnershipRepository`,
`PaymentGateway` (via payments input port `InitiateCharge`), `PricingService`, `EventPublisher`,
`Clock`.

**HLFR-COMMERCE-01 — Cart management.** The system must maintain a per-user cart of mixed item kinds
with correct stackability and totals. *Actors:* Fan. *Surfaces:* cart, buy buttons. *Rationale:*
precedes checkout.

- **LLFR-COMMERCE-01.1 — Get cart.** `GET /v1/me/cart` → `{ items: CartItem[], subtotal, fee, total,
  count }`. `fee = SERVICE_FEE (₵0.50)` when items > 0 else 0; `subtotal = Σ price×qty`;
  `total = subtotal + fee`; `count = Σ qty`. Auth: fan.
- **LLFR-COMMERCE-01.2 — Add item.** `POST /v1/me/cart/items` `{ id, kind, refId, qty?, metadata? }`
  → cart. `kind ∈ {track,album,album-rest,store,episode,season-pass,ticket}`. **Digital one-off**
  kinds (`track,album,album-rest,episode,season-pass`) are **non-stackable** — adding again is a no-op
  (not an error); `ticket`/`store`(merch) are stackable, qty clamped 1–99. Reject adding an item the
  caller already **owns** → 409 `ALREADY_OWNED`. `metadata` carries `licenseTier`/`merchVariants` for
  store items. *AC:* **Given** a track already in cart **When** add again **Then** cart unchanged
  (qty stays 1). **Given** a ticket **When** add twice **Then** qty=2.
- **LLFR-COMMERCE-01.3 — Update / remove line.** `PATCH /v1/me/cart/items/:lineId` `{ qty }` (clamp
  1–99; non-stackable → 409 `NOT_STACKABLE`) · `DELETE /v1/me/cart/items/:lineId` → cart. Auth: fan.

**HLFR-COMMERCE-02 — Checkout & ownership grant.** The system must charge the selected payment method,
and **on confirmed settlement** create ownership grants (expanding albums/season passes), record an
order, credit the creator ledger, and clear the cart. *Actors:* Fan. *Surfaces:* checkout, receipt,
buy-to-own unlock. *Rationale:* the revenue + ownership core (INV-1, INV-2, INV-4).

- **LLFR-COMMERCE-02.1 — Checkout (initiate).** `POST /v1/checkout` `{ paymentMethodId,
  idempotencyKey }` → `202 { orderId, paymentIntentId, status: pending }` (async MoMo) **or**
  `200 OrderSnapshot` (synchronously settled rails). Validates non-empty cart (409 `CART_EMPTY`),
  recomputes totals server-side (never trusts client), creates `order(status=pending)` + `order_line`s
  snapshotting price/title, and calls payments `InitiateCharge` with the **idempotency key** (INV-11,
  §9.2). Auth: fan. *AC:* **Given** a MoMo method **When** checkout **Then** an order is `pending`, a
  payment intent exists, and **no ownership is granted yet** (INV-1).
- **LLFR-COMMERCE-02.2 — Settlement → grant (event handler).** On `PaymentSettled(orderId)`: mark
  `order.status=paid`; create `ownership_grant`s for each line — expanding `album`/`album-rest` to all
  album track ids and `season-pass` to all premium episode ids of the show (INV-2); post creator
  credits to the ledger at the 70/30 split (INV-4) via payments; emit `OwnershipGranted` and a
  `sale` notification to each creator; clear the cart; produce the `OrderSnapshot` receipt with
  `reference` = `BZ-YYYY-NNNNN`. Idempotent on `orderId`. *AC:* **Given** a settled album purchase
  **Then** every track of the album is in `/me/owned`, the order is `paid`, and the creator's
  available/pending balance reflects 70% of the album price.
- **LLFR-COMMERCE-02.3 — Settlement failure.** On `PaymentFailed(orderId)`: set
  `order.status=failed`, leave cart intact, notify the fan; no grants. `GET` on the order shows the
  failure reason. *AC:* **Given** a failed charge **Then** no ownership and the cart is preserved for
  retry.
- **LLFR-COMMERCE-02.4 — Order history.** `GET /v1/me/orders` → `OrderSnapshot[]` (paged), newest
  first. Auth: fan.
- **LLFR-COMMERCE-02.5 — Ticket issuance.** When a `ticket` line settles, issue a `Ticket` (event,
  tier, holder, QR ref) and include it in the receipt; decrement tier availability (sold-out →
  `EventStatus=sold-out`). *AC:* **Given** the last ticket of a tier settles **Then** the tier reports
  `soldOut=true` and further adds → 409 `TIER_SOLD_OUT`.

**Schema (commerce):** `cart(id PK, account_id FK UNIQUE)`; `cart_item(id PK, cart_id FK, kind, ref_id,
title, subtitle, image, unit_price_minor, qty, stackable bool, metadata jsonb)`; `order(id PK,
account_id FK, reference UNIQUE, status enum, subtotal_minor, fee_minor, total_minor,
payment_intent_id, created_at)`; `order_line(id PK, order_id FK, kind, ref_id, title, unit_price_minor,
qty)`; `ownership_grant(id PK, account_id FK, track_id FK?, episode_id FK?, source_order_id FK,
granted_at, revoked_at?)`, UNIQUE `(account_id, track_id)` / `(account_id, episode_id)` where not
revoked.

### 6.6 Payments & Payouts (`payments`)

**Responsibilities [largely PROPOSAL]:** payment intents and provider integration (MoMo/card/bank),
asynchronous webhooks/callbacks, idempotency, reconciliation, a double-entry **ledger**, creator
balance accrual, payout methods, withdrawals (KYC-gated), payout batches, refunds, chargebacks, and
disputes. **Owned tables:** `payment_intent`, `payment_event`, `ledger_account`, `ledger_entry`,
`creator_balance`, `payout_method`, `withdrawal_request`, `payout_batch`, `payout_txn`, `kyc_record`,
`refund`, `dispute`, `dispute_event`. **Input ports:** `InitiateCharge`, `HandleProviderWebhook`,
`Reconcile`, `GetPayouts`, `RequestWithdrawal`, `AddPayoutMethod`, `RemovePayoutMethod`,
`SetDefaultPayoutMethod`, `RunWeeklyPayouts`, `SendSinglePayout`, `GetLedger`, `GetDispute`,
`RefundDispute`, `RejectDispute`, `EscalateDispute`, `IssueTip`. **Output ports:** `PaymentGateway`
(per-provider adapters), `LedgerRepository`, `PayoutRepository`, `KycProvider`, `EventPublisher`,
`Clock`, `IdGenerator`.

**HLFR-PAYMENTS-01 [PROPOSAL] — MoMo/card/bank charging with async settlement & idempotency.** The
system must initiate a charge against the chosen rail, handle the asynchronous provider
callback/webhook, apply idempotency and retry/timeout handling, and confirm settlement before any
value is granted. *Actors:* Fan (indirect via commerce); Payment provider. *Surfaces:* checkout, tips.
*Rationale:* MoMo is async; correctness of ownership depends on settlement (INV-1).

- **LLFR-PAYMENTS-01.1 — Initiate charge.** Input port `InitiateCharge(orderRef, amount, method,
  idempotencyKey)` → `PaymentIntent { id, status: pending, provider, providerRef }`. Same
  `idempotencyKey` returns the **same** intent (no double charge, §9.2). Selects provider adapter by
  `PayoutMethod.kind`/provider; on provider error → intent `failed` with reason. *AC:* **Given** the
  same idempotency key twice **Then** exactly one provider charge and one intent.
- **LLFR-PAYMENTS-01.2 — Provider webhook/callback.** `POST /v1/payments/webhooks/:provider` (public,
  **signature-verified** via `BEATZ_PAYMENT_WEBHOOK_SECRET`; invalid signature → 401) → `200`. Records
  a `payment_event`, transitions the intent `pending → settled|failed`, and emits
  `PaymentSettled`/`PaymentFailed`. **Idempotent** on provider event id (replays are no-ops).
  Untrusted/unknown ref → 202 (accept, ignore) to avoid provider retries storm. *AC:* **Given** a
  duplicate webhook **Then** the intent transitions at most once and exactly one `PaymentSettled` is
  emitted.
- **LLFR-PAYMENTS-01.3 — Timeout & retry.** A scheduler re-queries `pending` intents older than N
  minutes against the provider (`PaymentGateway.queryStatus`) and settles/fails them; after a max
  window, intent → `failed (timeout)`. *AC:* **Given** a never-delivered webhook **Then** the
  reconciliation poll eventually settles or fails the intent.
- **LLFR-PAYMENTS-01.4 — Reconciliation.** A daily job compares provider settlement records to the
  internal ledger and flags mismatches as a finance `AttentionItem` / risk signal. *AC:* **Given** a
  provider-settled charge with no matching ledger credit **Then** a reconciliation discrepancy is
  recorded for finance review.

**HLFR-PAYMENTS-02 [PROPOSAL] — Double-entry ledger & creator balance.** The system must record every
monetary movement as balanced double-entry rows and derive creator balances (70% sales/royalties, 90%
tips). *Actors:* system; Creator (reads). *Surfaces:* Studio payouts, Admin finance ledger.

- **LLFR-PAYMENTS-02.1 — Post sale split.** On `PaymentSettled` for a sale, post: debit
  `provider_clearing`, credit `creator_payable` 70%, credit `platform_revenue` 30% (percentages from
  `PlatformSettings`, INV-4); Σ balanced (INV-6). Tips: 90/10. Multi-creator splits subdivide the
  creator share per `split_entry` (INV-12). *AC:* **Given** a ₵10 settled sale **Then** ledger shows
  creator_payable +₵7.00 and platform_revenue +₵3.00, balanced.
- **LLFR-PAYMENTS-02.2 — Get payouts (creator).** `GET /v1/studio/payouts` → `{ available, pending,
  thisMonth, thisMonthDelta, lifetime, since, earnings[], bySource{sales,royalties,tips}, methods:
  PayoutMethod[], transactions: PayoutTxn[] }`. `available` = cleared creator_payable − cleared
  cash-outs (INV-6); pending = uncleared. Auth: artist.
- **LLFR-PAYMENTS-02.3 — Ledger (admin).** `GET /v1/admin/finance/ledger?type=&q=&page=` → paged
  `LedgerEntry[]`. Auth: admin finance/super-admin (403 otherwise).

**HLFR-PAYMENTS-03 [PROPOSAL] — Payout methods, withdrawals & KYC gating.** The system must let
creators manage payout methods and request withdrawals, gated by minimum amount, sufficient cleared
balance, and verified KYC; and let finance execute payouts (weekly batch or single). *Actors:*
Creator; Admin (finance). *Surfaces:* Studio payouts, Admin finance.

- **LLFR-PAYMENTS-03.1 — Manage payout methods.** `POST /v1/studio/payout-methods` (`{ label, detail,
  kind: momo|bank }` → `201 PayoutMethod`); `DELETE …/:id`; `PATCH …/:id/default`. Cannot delete the
  only/default method while a withdrawal is in flight (409). Auth: artist.
- **LLFR-PAYMENTS-03.2 — Request withdrawal.** `POST /v1/studio/payouts/withdraw` `{ amount, methodId,
  idempotencyKey }` → `202 WithdrawalRequest { status: pending, fee, arrival }`. Validations:
  `amount ≥ MIN_PAYOUT (₵10)` (422 `BELOW_MIN_PAYOUT`); `amount ≤ available` (409
  `INSUFFICIENT_BALANCE`); KYC `verified` (403 `KYC_REQUIRED`, INV-8). Fee per `withdrawalFee(kind,
  amount)` (bank ₵5; MoMo 1% min ₵1); `arrival` per `arrivalTime(kind)`. Posts a `Cash-out` ledger
  entry reserving funds. Auth: artist. *AC:* **Given** unverified KYC **When** withdraw **Then** 403
  `KYC_REQUIRED` and no ledger movement. **Given** ₵5 request **Then** 422 `BELOW_MIN_PAYOUT`.
- **LLFR-PAYMENTS-03.3 — Run weekly payouts.** `POST /v1/admin/finance/payouts/run-weekly` → batch
  pays all `ready` withdrawals (KYC-verified, ≥ min); skips KYC-blocked. Audited (INV-10). Auth:
  finance/super-admin. *AC:* **Given** mixed-eligibility creators **Then** only KYC-verified ones are
  paid and each payout appends an audit entry.
- **LLFR-PAYMENTS-03.4 — Send single payout.** `POST /v1/admin/finance/payouts/:id/send` → executes
  one; **blocks on KYC** (409 `KYC_BLOCKED`). Audited. Auth: finance/super-admin.

**HLFR-PAYMENTS-04 [PROPOSAL] — Refunds, chargebacks & disputes.** The system must support the dispute
lifecycle, ownership revocation on refund, ledger reversal/clawback, and admin adjudication. *Actors:*
Admin (finance); Fan/provider (initiation). *Surfaces:* Admin finance dispute screens.

- **LLFR-PAYMENTS-04.1 — Get dispute.** `GET /v1/admin/finance/disputes/:id` → detail + timeline
  (`dispute_event[]`). Auth: finance/super-admin.
- **LLFR-PAYMENTS-04.2 — Refund.** `POST /v1/admin/finance/disputes/:id/refund` `{ amount?, reason }`
  → refunds (full or partial), revokes the related `ownership_grant`(s) (INV-9), reverses the creator
  credit (clawback) and platform fee, transitions dispute `open → refunded`. Audited; logs amount.
  *AC:* **Given** a refunded purchase **Then** the buyer no longer owns the track (preview-gated again)
  and the creator's balance is reduced by the clawed-back share.
- **LLFR-PAYMENTS-04.3 — Reject / escalate.** `POST …/:id/reject` (`{ reason }`, → `rejected`) ·
  `POST …/:id/escalate` (→ `escalated`). Audited. Auth: finance/super-admin.

**HLFR-PAYMENTS-05 [DERIVED] — Tips.** The system must process instant MoMo tips to creators (podcast
and, where surfaced, artist), crediting 90% to the creator. *Actors:* Fan. *Surfaces:* podcast support
modal. (Endpoint defined in §6.8; ledger handled here at 90/10.)

**Schema (payments):** `payment_intent(id PK, order_ref, amount_minor, provider, provider_ref, status
enum, idempotency_key UNIQUE, created_at)`; `payment_event(id PK, intent_id FK, provider_event_id
UNIQUE, type, payload jsonb, received_at)`; `ledger_account(id PK, kind, owner_account_id?)`;
`ledger_entry(id PK, txn_id, account_id FK, direction, amount_minor, ref_type, ref_id, posted_at)`
with a balance check per `txn_id`; `creator_balance(account_id PK, available_minor, pending_minor,
lifetime_minor)`; `payout_method(id PK, account_id FK, kind, label, detail, is_default)`;
`withdrawal_request(id PK, account_id FK, amount_minor, fee_minor, method_id FK, status, requested_at)`;
`kyc_record(account_id PK, status enum {none,pending,verified,rejected}, verified_at)`; `dispute(id PK,
order_id FK, status, amount_minor, opened_at)`; `refund(id PK, dispute_id FK, amount_minor, at)`.

### 6.7 Store (`store`)

**Responsibilities:** the marketplace catalog (beats with license tiers, hi-fi tracks/albums, merch
with variants, exclusives with drop dates/stock) and product detail. **Owned tables:** `store_item`,
`license_option`, `merch_variant`. (Purchases flow through commerce §6.5.) **Input ports:**
`ListStore`, `GetStoreItem`. **Output ports:** `StoreRepository`, `SearchIndex`.

**HLFR-STORE-01 [DERIVED] — Marketplace browse.** The system must serve a filterable, sortable store
catalog and product detail matching the `StoreItem` shape. *Actors:* Fan. *Surfaces:* store overview +
tabs + product detail.

- **LLFR-STORE-01.1 — List store.** `GET /v1/store?type=&genre=&sort=` → paged `StoreItem[]`.
  `type ∈ {TRACK,ALBUM,BEAT_LICENSE,MERCH,EXCLUSIVE}`; `sort ∈ {popular,newest,price-asc,price-desc}`
  (default `popular` by `popularity`, `newest` by `createdAt`). Public. *AC:* **Given** `sort=price-asc`
  **Then** items are ordered by ascending `price.amount`.
- **LLFR-STORE-01.2 — Store item detail.** `GET /v1/store/:id` → `StoreItem` with type-specific fields:
  `licenseOptions: LicenseOption[]` (BEAT_LICENSE), `variants: MerchVariant[]` (MERCH), `quality`
  (TRACK/ALBUM hi-fi), `dropsAt`/`stockRemaining` (EXCLUSIVE). Unknown id → 404. Public. *AC:* **Given**
  a beat product **Then** `licenseOptions` carries LEASE/PREMIUM/EXCLUSIVE tiers with prices.

### 6.8 Podcasts (`podcasts`)

**Responsibilities:** podcast shows, episodes (free/premium/early-access with ownership), and instant
tips. **Owned tables:** `podcast`, `podcast_episode`. **Input ports:** `ListPodcasts`, `GetPodcast`,
`ListEpisodes`, `TipShow`. **Output ports:** `PodcastRepository`, `OwnershipReader`, `PaymentGateway`
(via payments `IssueTip`), `MediaService`, `EventPublisher`.

**HLFR-PODCAST-01 [DERIVED] — Podcast browse & gated playback.** The system must serve shows and
episodes with premium/early-access gating and per-caller ownership. *Actors:* Fan. *Surfaces:* podcasts
list, show detail.

- **LLFR-PODCAST-01.1 — List shows.** `GET /v1/podcasts?category=` → paged `Podcast[] { id, title,
  publisher, image, category, episodeCount?, seasonPassPrice?, supportsTips? }`. Public.
- **LLFR-PODCAST-01.2 — Show detail.** `GET /v1/podcasts/:id` → `Podcast`; 404 if unknown. Public.
- **LLFR-PODCAST-01.3 — List episodes.** `GET /v1/podcasts/:id/episodes` → `PodcastEpisode[]` with
  `isPremium`, `price`, `isOwned` (per caller via `OwnershipReader`), `isEarlyAccess`, `publicAt`.
  Premium unowned episodes stream a preview only (mirrors INV-3, OQ-6); early-access locked until
  `publicAt` unless owned. Public (ownership decoration requires auth). *AC:* **Given** a premium
  episode the caller doesn't own **Then** `isOwned=false` and full audio is withheld.

**HLFR-PODCAST-02 [DERIVED] — Tipping.** The system must process an instant MoMo tip to a show,
crediting the creator 90%. *Actors:* Fan. *Surfaces:* support modal.

- **LLFR-PODCAST-02.1 — Tip show.** `POST /v1/podcasts/:id/tip` `{ amount, paymentMethodId,
  idempotencyKey }` → `202 { status }`. `amount > 0` (422); routed through payments
  `IssueTip` (90/10, HLFR-PAYMENTS-05); emits `TipReceived` → `tip` notification to the creator.
  Idempotent. Auth: fan. *AC:* **Given** a ₵10 tip settles **Then** the creator nets ₵9.00 and a `tip`
  notification is delivered.

### 6.9 Events & Ticketing (`events`)

**Responsibilities:** event listings/detail with ticket tiers; ticket purchase flows through commerce
(`kind: ticket`, `refId: eventId:tier`). **Owned tables:** `event`, `ticket_tier`, `ticket`. **Input
ports:** `ListEvents`, `GetEvent`. (Issuance handled in commerce §6.5.5.) **Output ports:**
`EventRepository`, `OwnershipReader`.

**HLFR-EVENTS-01 [DERIVED] — Event discovery & ticketing data.** The system must serve event listings
and detail with ticket tiers and live availability. *Actors:* Fan. *Surfaces:* events list, event
detail.

- **LLFR-EVENTS-01.1 — List events.** `GET /v1/events?city=&category=` → paged `Event[]`.
  `category ∈ {Concert,Festival,Club Night,Listening Party,Tour}`. Public.
- **LLFR-EVENTS-01.2 — Event detail.** `GET /v1/events/:id` → `Event` incl. `ticketTiers: TicketTier[]`
  with `soldOut` reflecting live availability and `status ∈ {on-sale,selling-fast,sold-out}`. 404 if
  unknown. Public. *AC:* **Given** a tier with zero remaining **Then** that tier's `soldOut=true`.

### 6.10 Notifications (`notifications`)

**Responsibilities:** the in-app notification feed and outbound email/SMS delivery. **Owned tables:**
`notification`, `delivery_attempt`. **Input ports:** `ListNotifications`, `MarkAllRead`, `MarkOneRead`,
`Notify` (internal). **Output ports:** `NotificationRepository`, `Mailer`, `SmsSender`, `Clock`.

**HLFR-NOTIF-01 [DERIVED] — In-app notifications.** The system must maintain a per-user feed with
unread counts and read-state mutations, populated by domain events. *Actors:* Fan, Creator. *Surfaces:*
header bell, `/notifications`.

- **LLFR-NOTIF-01.1 — List notifications.** `GET /v1/me/notifications` → `{ items: AppNotification[],
  unread }`. `AppNotification { id, type: sale|tip|follower|payout|release|system, title, body, time,
  read, to? }`. Auth: any user.
- **LLFR-NOTIF-01.2 — Mark all read.** `POST /v1/me/notifications/read` → `204`. Idempotent.
- **LLFR-NOTIF-01.3 — Mark one read.** `POST /v1/me/notifications/:id/read` → `204`; non-owner →
  404. Idempotent.

**HLFR-NOTIF-02 [PROPOSAL] — Multi-channel delivery.** The system must fan notifications to email/SMS
per user preferences with retry and local capture in dev. *Actors:* system. *Surfaces:* none (backend).

- **LLFR-NOTIF-02.1 — Dispatch on event.** On `sale|tip|follower|payout|release|system` events, create
  an in-app notification and, if the recipient's `fan_settings.notif_json` opts in, enqueue email
  (`Mailer`) and/or SMS (`SmsSender`); record `delivery_attempt` with status; retry transient
  failures with backoff. In dev, email→Mailpit, SMS→capture stub. *AC:* **Given** a sale and a user
  opted into email **Then** an in-app notification exists and a captured email is sent.

### 6.11 Studio (creator) (`studio`)

**Responsibilities:** creator-facing aggregation and creator-owned config — profile, podcasts
management, insights/audience analytics reads, payouts (delegated to payments §6.6), and studio
settings. Releases live in `catalog` §6.2; this module composes views and owns profile/settings.
**Owned tables:** `studio_profile` (or shared with `artist_profile`), `studio_settings`,
`studio_podcast_show`, `studio_episode` (creator side of `podcasts`). **Input ports:** `GetStudioProfile`,
`SaveStudioProfile`, `ListStudioPodcastShows`, `CreatePodcastShow`, `ListStudioEpisodes`, `CreateEpisode`,
`UpdateEpisode`, `DeleteEpisode`, `GetAnalytics`, `GetAudience`, `GetStudioSettings`, `SaveStudioSettings`.
**Output ports:** `StudioRepository`, `AnalyticsReader`, `MediaService`, `EventPublisher`. **Auth: all
endpoints require `artist` role** (403 `ARTIST_REQUIRED` otherwise).

**HLFR-STUDIO-01 — Creator profile.** The system must let an artist read and save their public-facing
profile. *Surfaces:* studio profile.

- **LLFR-STUDIO-01.1 — Get/save profile.** `GET/PUT /v1/studio/profile` →/← `StudioProfile {
  displayName, username, hometown, genres[], bio, avatar, banner, links{instagram,twitter,youtube,
  website}, shows[], featuredTrackId, bookingEmail, pressAssets[] }`. `username` unique, slug-safe
  (409 `USERNAME_TAKEN`); `genres ⊆ Genre`. Auth: artist.

**HLFR-STUDIO-02 — Creator podcasts.** The system must let an artist manage shows and upload episodes
(free/premium/early-access), mirroring the music pipeline (70% share, publish-now or schedule).
*Surfaces:* studio podcasts + new-episode upload.

- **LLFR-STUDIO-02.1 — Shows.** `GET /v1/studio/podcasts/shows` → `StudioPodcastShow[] { id, title,
  category }`; `POST` `{ title, category }` → `201`. Auth: artist.
- **LLFR-STUDIO-02.2 — Episodes list.** `GET /v1/studio/podcasts/episodes` → `StudioEpisode[] { id,
  showId, showTitle, title, duration, status: published|scheduled|draft, premium, price, publishedAt,
  plays }`. Auth: artist.
- **LLFR-STUDIO-02.3 — Create episode.** `POST /v1/studio/podcasts/episodes` multipart audio +
  `{ showId|newShow, title, description, cover?, visibility: public|scheduled, date?, premium, price?,
  earlyAccess? }` → `201 StudioEpisode`. `premium ⇒ price > 0` (422); `visibility=scheduled ⇒ date`
  required & future (422); delegates upload/transcode to `MediaService`. Emits `EpisodePublished`
  (now) or schedules it (INV-7). Auth: artist. *AC:* **Given** a premium scheduled episode with a
  future date **Then** it is `scheduled`, not publicly listed until the date, then `published`.
- **LLFR-STUDIO-02.4 — Edit/delete episode.** `PATCH/DELETE /v1/studio/podcasts/episodes/:id` → `200`/
  `204`. Delete blocked once `published` with owners (409, OQ-8). Auth: owning artist.

**HLFR-STUDIO-03 — Insights & audience.** The system must serve creator analytics over selectable
ranges. *Surfaces:* studio analytics, audience.

- **LLFR-STUDIO-03.1 — Analytics.** `GET /v1/studio/analytics?range=7d|28d|90d|12m|all` → KPIs +
  per-metric series (streams/sales/followers/tips) + top tracks + countries + revenue split + sources
  + engagement. Backed by `analytics` rollups (§6.14). Auth: artist. *AC:* **Given** `range=28d` **Then**
  every series has points within the window and KPIs are consistent with the series.
- **LLFR-STUDIO-03.2 — Audience.** `GET /v1/studio/audience` → listeners, followers, superfans,
  cities, gender, age, top fans. Auth: artist.

**HLFR-STUDIO-04 — Payouts & settings.** (Payouts delegate to §6.6 HLFR-PAYMENTS-02/03.)

- **LLFR-STUDIO-04.1 — Payouts view.** `GET /v1/studio/payouts` — see LLFR-PAYMENTS-02.2.
- **LLFR-STUDIO-04.2 — Studio settings.** `GET/PUT /v1/studio/settings` → notifications, sales
  defaults (`trackPrice`, `releaseVisibility`, `autoExplicit`, `allowOffers`), payouts, privacy, team,
  security. Auth: artist.

### 6.12 Admin / Moderation (`admin`)

**Responsibilities:** platform operations across overview/health, users, catalog moderation,
moderation queue, finance ops (delegating money to §6.6), editorial, trust & safety, support,
compliance, and platform settings — **all enforced by RBAC scopes and all mutations audited**.
**Owned tables:** `moderation_case`, `risk_signal`, `support_ticket`, `support_message`,
`compliance_request`, `featured_slot`, `push_item`, `curated_playlist`, `platform_settings`,
`feature_flag`. **Input ports:** one per operation below. **Output ports:** repositories +
`AuditWriter` + readers into other modules. **RBAC (server-enforced, §9.1):** super-admin = all;
finance = payouts/ledger/disputes; moderator = moderation/takedowns; editor = editorial; support =
user lookup + read-only elsewhere.

**HLFR-ADMIN-01 — Platform overview & health.** *Surfaces:* `/admin`, `/admin/health`.
- **LLFR-ADMIN-01.1 — Overview.** `GET /v1/admin/overview?range=24h|7d|30d` → KPIs, GMV series,
  needs-attention, top artists, payment mix. Auth: any admin (read).
- **LLFR-ADMIN-01.2 — Health.** `GET /v1/admin/health` → status, metrics, concurrent-listeners series,
  incidents. Auth: any admin.

**HLFR-ADMIN-02 — User administration.** *Surfaces:* `/admin/users`.
- **LLFR-ADMIN-02.1 — List/detail.** `GET /v1/admin/users?q=&filter=fans|artists|verified|suspended&
  page=&size=` → paged + counts; `GET /v1/admin/users/:id` → detail (activity, orders, devices, action
  log). Auth: any admin (support read-only).
- **LLFR-ADMIN-02.2 — Verify artist.** `POST /v1/admin/users/:id/verify` → `200`; audited. Auth:
  super-admin/moderator (OQ-1). Emits `ArtistVerified`.
- **LLFR-ADMIN-02.3 — Suspend.** `POST /v1/admin/users/:id/suspend` `{ reason }` (**required**, 422 if
  missing) → sets `status=suspended`, audited (INV-10), emits `UserSuspended`. Auth: super-admin/
  moderator. *AC:* **Given** no reason **Then** 422; **Given** a reason **Then** user suspended and an
  audit entry with actor/target/reason exists.
- **LLFR-ADMIN-02.4 — Reactivate.** `POST /v1/admin/users/:id/reactivate` → `status=active`; audited.
- **LLFR-ADMIN-02.5 — Impersonate.** `POST /v1/admin/users/:id/impersonate` → scoped, time-boxed
  session token; **heavily audited**; super-admin only. *AC:* **Given** impersonation **Then** an audit
  entry records actor, target, and expiry.
- **LLFR-ADMIN-02.6 — Data export.** `POST /v1/admin/users/:id/data-export` → enqueues a DSAR export
  job; audited. Auth: super-admin/support (OQ-1).

**HLFR-ADMIN-03 — Catalog moderation.** *Surfaces:* `/admin/catalog`.
- **LLFR-ADMIN-03.1 — List/detail.** `GET /v1/admin/catalog?status=pending|published|takedown&q=&page=`
  (+ counts); `GET /v1/admin/catalog/:id` (tracklist, ISRC/UPC, rights/splits, action log). Auth:
  moderator/super-admin.
- **LLFR-ADMIN-03.2 — Approve/flag/takedown.** `POST /v1/admin/catalog/:id/{approve|flag|takedown}`;
  `takedown` requires `{ reason }` (422 if missing). Drives release transitions (§6.2.5): approve →
  `scheduled`/`live`; takedown → `takedown` + emits `ContentTakenDown` (pulls from public reads).
  Audited. *AC:* **Given** an `in_review` release **When** approve with a future date **Then** it
  becomes `scheduled` and an audit entry is written.

**HLFR-ADMIN-04 — Moderation queue.** *Surfaces:* `/admin/moderation`.
- **LLFR-ADMIN-04.1 — Queue + actions.** `GET /v1/admin/moderation?status=&type=` (+ SLA/escalation
  summary); `POST /v1/admin/moderation/:id/{review|approve|remove|escalate|dismiss}`. Audited. Auth:
  moderator/super-admin.

**HLFR-ADMIN-05 — Finance operations.** (Money mechanics in §6.6.) *Surfaces:* `/admin/finance`.
- **LLFR-ADMIN-05.1 — Finance overview.** `GET /v1/admin/finance?range=` → GMV, fees, payouts due,
  MoMo float, provider mix, pending payouts, disputes. Auth: finance/super-admin.
- **LLFR-ADMIN-05.2 — Payout runs.** see LLFR-PAYMENTS-03.3/03.4. **LLFR-ADMIN-05.3 — Ledger.** see
  LLFR-PAYMENTS-02.3. **LLFR-ADMIN-05.4 — Disputes.** see LLFR-PAYMENTS-04.*.

**HLFR-ADMIN-06 — Editorial.** *Surfaces:* `/admin/editorial`.
- **LLFR-ADMIN-06.1 — Featured/push/playlists.** `GET/PUT /v1/admin/editorial/featured` (ordered
  slots, feeds `/home`); `GET/POST /v1/admin/editorial/push` (scheduled push); `GET/POST
  /v1/admin/editorial/playlists` (curated). Audited. Auth: editor/super-admin.

**HLFR-ADMIN-07 — Trust & safety.** *Surfaces:* `/admin/trust`.
- **LLFR-ADMIN-07.1 — Risk signals.** `GET /v1/admin/risk` (KPIs + `RiskSignal[]`); `POST
  /v1/admin/risk/:id/{review|clear|ban}`. `ban` sets account banned + revokes sessions; audited.
  Auth: moderator/super-admin.

**HLFR-ADMIN-08 — Support.** *Surfaces:* `/admin/support`.
- **LLFR-ADMIN-08.1 — Tickets.** `GET /v1/admin/support/tickets?status=&q=`; `GET …/:id` (thread);
  `POST …/:id/reply { text }`; `POST …/:id/{assign|resolve}`. Auth: support+ (all admins).

**HLFR-ADMIN-09 — Compliance.** *Surfaces:* `/admin/compliance`.
- **LLFR-ADMIN-09.1 — Requests + actions.** `GET /v1/admin/compliance?type=DSAR-export|DSAR-delete|
  Takedown|Tax` (+ due dates, `status ∈ {new,in_progress,completed,overdue}`); `POST /v1/admin/
  compliance/:id/{start|complete}`; `POST …/:id/{export|notice}` (DSAR data export / DMCA notice).
  Audited; ties to the Ghana Data Protection Act (§10). Auth: super-admin (OQ-1).

**HLFR-ADMIN-10 — Platform settings & feature flags.** *Surfaces:* `/admin/settings`.
- **LLFR-ADMIN-10.1 — Settings.** `GET/PUT /v1/admin/settings` → `PlatformSettings { platformFeePct,
  payoutDay, payoutMinimum, defaultCurrency, maintenanceMode, providers{momo,vodafone,airteltigo,card,
  bank}, flags{artistSignups,podcasts,events,tipping,fanMessaging} }`. **super-admin only** (403
  otherwise). Changing `platformFeePct` is audited and takes effect for **future** settlements only.
  *AC:* **Given** a moderator **When** PUT settings **Then** 403; **Given** super-admin changes fee
  28→30 **Then** an audit entry records the change and new sales split at 30%.

**HLFR-ADMIN-11 — Audit trail.** *Surfaces:* `/admin/audit`. (Cross-cutting writer in §6.15.)
- **LLFR-ADMIN-11.1 — Read audit.** `GET /v1/admin/audit?type=&q=&page=` → paged `AuditEntry[] { id,
  actor, action, target, type, time }`. `type ∈ {user,catalog,finance,moderation,settings,editorial}`.
  Auth: super-admin (read; OQ-1). Write side is automatic on every privileged mutation (INV-10).

### 6.13 Search & discovery indexing (`catalog`/`search` — [PROPOSAL])

**HLFR-SEARCH-01 [PROPOSAL] — Search indexing.** The system must maintain a search index over tracks,
artists, albums, playlists, store items, podcasts, and events, updated as catalog changes, to back
`/search`, `/store`, and discovery. *Output port:* `SearchIndex` (Postgres full-text/`pg_trgm` for v1;
pluggable to OpenSearch later).
- **LLFR-SEARCH-01.1 — Index lifecycle.** On create/update/takedown of indexable entities, upsert/
  remove index documents (via domain events). *AC:* **Given** a release goes live **Then** its tracks
  appear in `/search` results within the indexing SLA.
- **LLFR-SEARCH-01.2 — Anti-manipulation in ranking.** Popularity inputs (plays) exclude flagged bot
  plays (ties to §6.3.2 and risk signals).

### 6.14 Media pipeline (`media` — [PROPOSAL, shared infra])

**HLFR-MEDIA-01 [PROPOSAL] — Upload, validate, transcode, deliver.** The system must accept audio/
artwork uploads, validate format and safety, transcode audio to streamable HLS (plus a 30s preview
clip), process artwork, lay assets out in object storage, and serve them via signed, expiring URLs —
full only to owners, preview to others. *Output port:* `MediaService`. *Surfaces:* release/episode
upload; playback delivery.
- **LLFR-MEDIA-01.1 — Upload.** Multipart (and resumable for large files, OQ-10) to the private
  originals bucket; accept WAV/FLAC audio and PNG/JPG artwork; reject others (422 `UNSUPPORTED_FORMAT`),
  oversize (413), and virus-positive (422 `FILE_REJECTED`). Returns an asset handle + probed duration.
- **LLFR-MEDIA-01.2 — Transcode.** Produce HLS renditions and a **30s preview** rendition (the
  server-side enforcement of INV-3); store in the delivery bucket; mark the track/episode `ready`.
  *AC:* **Given** an uploaded WAV **Then** a full HLS rendition and a ≤30s preview rendition exist and
  the track flips to `ready`.
- **LLFR-MEDIA-01.3 — Signed delivery.** Issue time-boxed signed URLs (`expiresAt`) per
  LLFR-PLAYBACK-01.1; full rendition only when ownership is confirmed; preview rendition otherwise.
  *AC:* **Given** a non-owner's preview URL **Then** it expires at `expiresAt` and cannot retrieve the
  full rendition.

### 6.15 Cross-cutting modules: Analytics, Audit, Platform/Scheduler

**HLFR-ANALYTICS-01 [PROPOSAL] — Rollups.** The system must roll up `play_event`s and settled sales/
tips/follows into time-bucketed series feeding Studio insights (§6.11.3) and Admin overview (§6.12.1).
- **LLFR-ANALYTICS-01.1 — Aggregation jobs.** Scheduled jobs maintain `sales_rollup`, `audience_rollup`,
  and plays counters by range; reads are served from rollups, not raw events. *AC:* **Given** seeded
  events **Then** `GET /studio/analytics?range=28d` returns consistent KPIs.

**HLFR-AUDIT-01 [DERIVED] — Append-only audit.** The system must append an `AuditEntry` for every
privileged mutation (INV-10). *Output port:* `AuditWriter` invoked by an application-layer interceptor
around audited use cases (actor from JWT, action, target, type, time). Append-only; never updated/
deleted. *AC:* **Given** any audited mutation **Then** exactly one immutable audit row is written and
is visible via LLFR-ADMIN-11.1.

**HLFR-PLATFORM-01 [PROPOSAL] — Feature flags, settings & scheduled jobs.** The system must source
behavioral flags and economic constants from `PlatformSettings`/`FeatureFlag` at runtime, expose
maintenance mode, and run scheduled jobs.
- **LLFR-PLATFORM-01.1 — Flag enforcement.** Guard `artistSignups`, `podcasts`, `events`, `tipping`,
  `fanMessaging` at the relevant endpoints (403 `FEATURE_DISABLED` when off). `maintenanceMode=true`
  returns 503 `MAINTENANCE` for non-admin write traffic.
- **LLFR-PLATFORM-01.2 — Scheduled jobs.** `quarkus-scheduler` jobs: release/episode go-live (INV-7),
  weekly payout reminder/run window (Friday), payment reconciliation/timeout poll, digest emails,
  analytics rollups, search reindex. Each is idempotent and observable. *AC:* **Given** a scheduled
  release whose time has passed **Then** the go-live job publishes it exactly once.

---

## 7. Functional requirements catalog & traceability matrix

The catalog below consolidates every HLFR and shows the HLFR → LLFR → work unit → owning module →
related `API-CONTRACT.md` section/endpoints, so coverage is auditable and nothing is orphaned. (Work
unit IDs `WU-*` are defined in §8.)

| HLFR | Capability | LLFRs | Work units | Module | API-CONTRACT endpoints |
|---|---|---|---|---|---|
| HLFR-IDENTITY-01 | Registration & auth | 01.1–01.5 | WU-IDN-1, WU-IDN-2 | identity | §2 `/auth/*`, `/me/password/reset` |
| HLFR-IDENTITY-02 | Session & self-service | 02.1–02.3 | WU-IDN-2, WU-IDN-3 | identity | §2 `/me`, `/me/become-artist`, `/me/settings` |
| HLFR-IDENTITY-03 | Admin team & RBAC | 03.1–03.3 | WU-IDN-4 | identity | §14 `/admin/team*` |
| HLFR-CATALOG-01 | Public catalog | 01.1–01.7 | WU-CAT-1, WU-CAT-2 | catalog | §3 `/home`,`/search`,`/artists*`,`/albums*`,`/tracks*`,`/playlists*` |
| HLFR-CATALOG-02 | Release lifecycle | 02.1–02.5 | WU-CAT-3, WU-CAT-4, WU-MED-1 | catalog | §11 `/studio/releases*` |
| HLFR-PLAYBACK-01 | Ownership-aware streaming | 01.1–01.2 | WU-PLY-1 | playback | §4 `/tracks/:id/stream`,`/play` |
| HLFR-LIBRARY-01 | Collection mgmt | 01.1–01.6 | WU-LIB-1 | library | §5 `/me/collection`,`/me/likes*`,`/me/follows*`,`/me/saved*`,`/me/playlists*`,`/me/owned` |
| HLFR-COMMERCE-01 | Cart | 01.1–01.3 | WU-COM-1 | commerce | §6 `/me/cart*` |
| HLFR-COMMERCE-02 | Checkout & ownership | 02.1–02.5 | WU-COM-2, WU-PAY-1 | commerce | §6 `/checkout`,`/me/orders` |
| HLFR-PAYMENTS-01 | Charging & webhooks | 01.1–01.4 | WU-PAY-1, WU-PAY-2 | payments | §6 `/checkout` (+ `/payments/webhooks/*` proposed) |
| HLFR-PAYMENTS-02 | Ledger & balance | 02.1–02.3 | WU-PAY-3 | payments | §11 `/studio/payouts`; §12 `/admin/finance/ledger` |
| HLFR-PAYMENTS-03 | Payouts & KYC | 03.1–03.4 | WU-PAY-4 | payments | §11 `/studio/payouts/withdraw`,`/payout-methods`; §12 `/admin/finance/payouts*` |
| HLFR-PAYMENTS-04 | Refunds & disputes | 04.1–04.3 | WU-PAY-5 | payments | §12 `/admin/finance/disputes*` |
| HLFR-PAYMENTS-05 | Tips | (see 02.1) | WU-PAY-3, WU-POD-2 | payments | §8 `/podcasts/:id/tip` |
| HLFR-STORE-01 | Marketplace browse | 01.1–01.2 | WU-STO-1 | store | §7 `/store*` |
| HLFR-PODCAST-01 | Podcast browse/gating | 01.1–01.3 | WU-POD-1 | podcasts | §8 `/podcasts*` |
| HLFR-PODCAST-02 | Tipping | 02.1 | WU-POD-2 | podcasts | §8 `/podcasts/:id/tip` |
| HLFR-EVENTS-01 | Events & tickets | 01.1–01.2 | WU-EVT-1 | events | §9 `/events*` |
| HLFR-NOTIF-01 | In-app notifications | 01.1–01.3 | WU-NOT-1 | notifications | §10 `/me/notifications*` |
| HLFR-NOTIF-02 | Multi-channel delivery | 02.1 | WU-NOT-2 | notifications | (proposed) |
| HLFR-STUDIO-01 | Creator profile | 01.1 | WU-STU-1 | studio | §11 `/studio/profile` |
| HLFR-STUDIO-02 | Creator podcasts | 02.1–02.4 | WU-STU-2, WU-MED-1 | studio | §11 `/studio/podcasts*` |
| HLFR-STUDIO-03 | Insights & audience | 03.1–03.2 | WU-STU-3, WU-ANA-1 | studio/analytics | §11 `/studio/analytics`,`/studio/audience` |
| HLFR-STUDIO-04 | Payouts & settings | 04.1–04.2 | WU-PAY-4, WU-STU-4 | studio | §11 `/studio/payouts`,`/studio/settings` |
| HLFR-ADMIN-01 | Overview & health | 01.1–01.2 | WU-ADM-1 | admin | §12 `/admin/overview`,`/admin/health` |
| HLFR-ADMIN-02 | User admin | 02.1–02.6 | WU-ADM-2 | admin | §12 `/admin/users*` |
| HLFR-ADMIN-03 | Catalog moderation | 03.1–03.2 | WU-ADM-3 | admin | §12 `/admin/catalog*` |
| HLFR-ADMIN-04 | Moderation queue | 04.1 | WU-ADM-3 | admin | §12 `/admin/moderation*` |
| HLFR-ADMIN-05 | Finance ops | 05.1–05.4 | WU-PAY-4, WU-PAY-5 | admin/payments | §12 `/admin/finance*` |
| HLFR-ADMIN-06 | Editorial | 06.1 | WU-ADM-4 | admin | §12 `/admin/editorial*` |
| HLFR-ADMIN-07 | Trust & safety | 07.1 | WU-ADM-5 | admin | §12 `/admin/risk*` |
| HLFR-ADMIN-08 | Support | 08.1 | WU-ADM-6 | admin | §12 `/admin/support*` |
| HLFR-ADMIN-09 | Compliance | 09.1 | WU-ADM-7 | admin | §12 `/admin/compliance*` |
| HLFR-ADMIN-10 | Settings & flags | 10.1 | WU-ADM-8, WU-PLT-1 | admin/platform | §14 `/admin/settings` |
| HLFR-ADMIN-11 | Audit read | 11.1 | WU-AUD-1 | audit | §13 `/admin/audit` |
| HLFR-SEARCH-01 | Search indexing | 01.1–01.2 | WU-SRCH-1 | catalog | §3 `/search`; §7 `/store` |
| HLFR-MEDIA-01 | Media pipeline | 01.1–01.3 | WU-MED-1 | media | §4,§11 uploads/stream |
| HLFR-ANALYTICS-01 | Rollups | 01.1 | WU-ANA-1 | analytics | §11,§12 insights |
| HLFR-AUDIT-01 | Append-only audit | 01.1 | WU-AUD-1 | audit | §13 |
| HLFR-PLATFORM-01 | Flags/settings/jobs | 01.1–01.2 | WU-PLT-1, WU-PLT-2 | platform | §14; cross-cutting |

---

## 8. Feature breakdown & work units

Each work unit is small enough to implement and verify in one agent iteration, maps to one or more
LLFRs, and lists what it touches and its dependencies. **Definition of done (applies to every WU):**
passing unit **and** integration tests; **contract conformance** to `API-CONTRACT.md` (response shapes
validate against the frontend types — verified by an OpenAPI/contract test); Flyway migrations included
and idempotent; the WU runs under the Docker Compose stack; **no violation of the hexagonal dependency
rule** (ArchUnit green); audited where it mutates privileged state; idempotency keys where money/side
effects are involved.

| WU | Scope (LLFRs) | Ports/adapters touched | Reads/Writes | Depends on |
|---|---|---|---|---|
| **WU-PLT-1** | Platform kernel: Money VO (minor units, INV-11), error model, pagination, clock/ids, `PlatformSettings` + flag enforcement (LLFR-PLATFORM-01.1, ADMIN-10.1) | platform domain; settings repo; flag interceptor | platform_settings, feature_flag | — |
| **WU-IDN-1** | Account model, signup/login, password hashing, JWT issue (IDENTITY-01.1/01.2/01.4) | RegisterFan/Login ports; account repo; TokenIssuer; CredentialHasher | account, credential | WU-PLT-1 |
| **WU-IDN-2** | `/me`, social login, password reset (IDENTITY-01.3/01.5, 02.1) | GetCurrentAccount/SocialLogin ports; SocialVerifier; Mailer | account, social_identity, reset_token | WU-IDN-1 |
| **WU-IDN-3** | Become-artist + fan settings (IDENTITY-02.2/02.3) | UpgradeToArtist/UpdateFanSettings; profile repo | account, fan_settings, artist_profile | WU-IDN-1 |
| **WU-IDN-4** | Admin members + RBAC scope enforcement (IDENTITY-03.*) | admin team ports; RBAC filter | admin_member | WU-IDN-1, WU-PLT-1 |
| **WU-AUD-1** | Audit writer interceptor + read endpoint (AUDIT-01, ADMIN-11.1) | AuditWriter port; audit repo | audit_entry | WU-IDN-4 |
| **WU-CAT-1** | Catalog entities + read endpoints artists/albums/tracks/playlists/lyrics (CATALOG-01.4–01.7) | catalog read ports; catalog repo | artist_profile, album, track, playlist, lyrics | WU-PLT-1 |
| **WU-CAT-2** | Home feed + browse + search read (CATALOG-01.1–01.3) | GetHomeFeed/Search; search index | (reads) | WU-CAT-1, WU-SRCH-1 |
| **WU-MED-1** | Media upload→validate→transcode→signed URL (MEDIA-01.*) | MediaService port; S3 adapter; transcoder | object storage | WU-PLT-1 |
| **WU-CAT-3** | Release wizard submit + manage + track upload (CATALOG-02.1–02.4) | release ports; catalog repo; MediaService | release, release_track, split_entry | WU-CAT-1, WU-MED-1, WU-IDN-3 |
| **WU-CAT-4** | Release state machine + scheduled go-live (CATALOG-02.5, PLATFORM-01.2) | release domain; scheduler | release | WU-CAT-3, WU-PLT-2 |
| **WU-PLY-1** | Stream URL (ownership-aware) + record play (PLAYBACK-01.*) | GetStreamUrl/RecordPlay; MediaService; OwnershipReader | play_event | WU-MED-1, WU-CAT-1, WU-COM-2 |
| **WU-LIB-1** | Collection: likes/follows/saved/user playlists/owned (LIBRARY-01.*) | collection ports; collection repo; OwnershipReader | liked_track, followed_*, saved_album, user_playlist* | WU-IDN-1, WU-CAT-1 |
| **WU-COM-1** | Cart with stackability + totals (COMMERCE-01.*) | cart ports; cart repo; PricingService | cart, cart_item | WU-PLT-1, WU-CAT-1 |
| **WU-PAY-1** | PaymentIntent + InitiateCharge + idempotency (PAYMENTS-01.1) | PaymentGateway port; payment repo | payment_intent | WU-PLT-1 |
| **WU-PAY-2** | Provider webhooks + timeout poll + reconciliation (PAYMENTS-01.2–01.4) | webhook adapter; scheduler | payment_event | WU-PAY-1, WU-PLT-2 |
| **WU-COM-2** | Checkout orchestration + settlement→ownership grant + orders + ticket issuance (COMMERCE-02.*) | Checkout port; OwnershipRepo; event handlers | order, order_line, ownership_grant, ticket | WU-COM-1, WU-PAY-1, WU-PAY-3 |
| **WU-PAY-3** | Double-entry ledger + creator balance + split posting + tips (PAYMENTS-02.*, 05) | LedgerRepo; balance domain | ledger_*, creator_balance | WU-PAY-1 |
| **WU-PAY-4** | Payout methods + withdrawals (KYC) + admin payout runs (PAYMENTS-03.*) | PayoutRepo; KycProvider | payout_method, withdrawal_request, payout_*, kyc_record | WU-PAY-3, WU-IDN-4 |
| **WU-PAY-5** | Refunds/chargebacks/disputes + ownership revocation + clawback (PAYMENTS-04.*) | dispute ports; OwnershipRepo | dispute, dispute_event, refund | WU-PAY-3, WU-COM-2 |
| **WU-STO-1** | Store catalog + detail (STORE-01.*) | store ports; store repo; search index | store_item, license_option, merch_variant | WU-CAT-1, WU-SRCH-1 |
| **WU-POD-1** | Podcast shows/episodes browse + gating (PODCAST-01.*) | podcast read ports; OwnershipReader | podcast, podcast_episode | WU-CAT-1, WU-MED-1 |
| **WU-POD-2** | Tipping (PODCAST-02.1) | TipShow → IssueTip | (ledger via WU-PAY-3) | WU-PAY-3, WU-POD-1 |
| **WU-EVT-1** | Events browse + detail + tier availability (EVENTS-01.*) | event ports; event repo | event, ticket_tier | WU-CAT-1 |
| **WU-NOT-1** | In-app notifications feed + read state (NOTIF-01.*) | notification ports; repo | notification | WU-IDN-1 |
| **WU-NOT-2** | Email/SMS dispatch on events with retry (NOTIF-02.1) | Mailer/SmsSender; event handlers | delivery_attempt | WU-NOT-1, WU-PLT-2 |
| **WU-STU-1** | Studio profile (STUDIO-01.1) | profile ports | studio_profile | WU-IDN-3 |
| **WU-STU-2** | Studio podcast shows/episodes create/manage (STUDIO-02.*) | studio podcast ports; MediaService | studio_podcast_show, studio_episode | WU-MED-1, WU-POD-1 |
| **WU-ANA-1** | Analytics rollups (ANALYTICS-01.1) | rollup jobs | sales_rollup, audience_rollup | WU-PLY-1, WU-COM-2, WU-PLT-2 |
| **WU-STU-3** | Studio analytics/audience reads (STUDIO-03.*) | AnalyticsReader | (reads rollups) | WU-ANA-1 |
| **WU-STU-4** | Studio settings (STUDIO-04.2) | settings ports | studio_settings | WU-IDN-3 |
| **WU-SRCH-1** | Search index lifecycle (SEARCH-01.*) | SearchIndex port (pg_trgm) | search docs | WU-CAT-1 |
| **WU-ADM-1..8** | Admin overview/health, users, catalog/queue moderation, editorial, risk, support, compliance, settings (ADMIN-01..10) | admin ports; readers; AuditWriter | moderation_case, risk_signal, support_*, compliance_request, featured_slot, push_item, curated_playlist | WU-IDN-4, WU-AUD-1, relevant domain WUs |
| **WU-PLT-2** | Scheduler infrastructure + job registration (PLATFORM-01.2) | quarkus-scheduler | (n/a) | WU-PLT-1 |

### 8.1 Sequencing / dependency graph (valid build order)

Foundations first, then identity & catalog, then commerce + payments, then payouts/disputes, then the
creator/admin surfaces and proposals:

**Phase 0 (foundations):** WU-PLT-1 → WU-PLT-2 → WU-AUD-1 (after IDN-4) ; WU-MED-1.
**Phase 1 (identity & catalog):** WU-IDN-1 → WU-IDN-2, WU-IDN-3, WU-IDN-4 ; WU-CAT-1 → WU-SRCH-1 →
WU-CAT-2 ; WU-CAT-3 → WU-CAT-4 ; WU-LIB-1.
**Phase 2 (commerce & payments):** WU-PAY-1 → WU-PAY-2, WU-PAY-3 ; WU-COM-1 → WU-COM-2 (needs PAY-1/3)
; WU-PLY-1 (needs COM-2 for ownership).
**Phase 3 (money completion):** WU-PAY-4 (payouts/KYC) ; WU-PAY-5 (refunds/disputes).
**Phase 4 (surfaces & proposals):** WU-STO-1, WU-POD-1 → WU-POD-2, WU-EVT-1, WU-NOT-1 → WU-NOT-2,
WU-STU-1..4, WU-ANA-1 → WU-STU-3, WU-ADM-1..8.

The strict rules the graph encodes: **identity + persistence foundations before commerce; payment
charging before ledger; ledger before payouts; checkout/ownership before playback unlock and before
refunds; analytics rollups before insight reads; audit + RBAC before any admin mutation.**

### 8.2 Note on downstream spec expansion

This PRD is the **index/root**. Each §6 module should expand into its own detailed spec file (the
leaves) when an agent picks it up — e.g. `specs/payments.md`, `specs/commerce.md`, `specs/media.md` —
carrying the LLFR IDs forward verbatim. Natural follow-on artifacts: the per-module spec files, the
Flyway migration set, the `docker-compose.yml`, and an OpenAPI document generated by
`quarkus-smallrye-openapi` and contract-tested against `API-CONTRACT.md`.

---

## 9. Cross-cutting concerns

### 9.1 AuthN / authZ & roles

Stateless Bearer JWT; `sub` = account id, `roles` = `fan`/`artist` plus admin scope(s). Fan endpoints
require any authenticated account; studio endpoints require `artist` (403 `ARTIST_REQUIRED`); admin
endpoints require the **specific scope** per §6.12 (super-admin = all; finance = payouts/ledger/
disputes; moderator = moderation/takedowns; editor = editorial; support = user lookup + read-only
elsewhere). Authorization is enforced in the inbound adapter via a role/scope filter **and**
re-checked in the application layer for resource ownership (e.g. a creator may only touch their own
releases; a fan only their own cart/collection). Passwords hashed with Argon2id; impersonation
produces a scoped, short-lived token and is heavily audited.

### 9.2 Payments, idempotency & money handling

Every money mutation (`/checkout`, `/withdraw`, `/tip`, payout runs, refunds) requires an
**idempotency key**; the same key returns the original result without repeating the side effect. Money
is stored in **integer minor units (pesewas)** (INV-11) and exposed as `{ amount: decimal cedis,
currency: "GHS" }`; rounding is half-up to 2 dp only at API/ledger boundaries; the 70/30 split, 90/10
tip split, 24% bundle discount, and ₵0.50 service fee are computed on minor units and reconciled so Σ
debits = Σ credits. Ownership is granted **only** on confirmed settlement (INV-1); provider webhooks
are signature-verified and idempotent on provider event id; a reconciliation job and a timeout poll
guarantee eventual consistency between provider and ledger.

### 9.3 Media storage, transcoding & signed/expiring preview URLs

Originals in a private bucket; HLS renditions + a **server-clipped 30s preview** rendition in a
delivery bucket. `/tracks/:id/stream` returns a signed URL with `expiresAt`; the **preview limit is
enforced server-side** by serving the 30s rendition to non-owners (the client timer is advisory). Full
renditions are issued only when ownership is confirmed. Uploads are format- and safety-validated; large
uploads use resumable multipart (OQ-10).

### 9.4 Notifications, error model, pagination, validation

Notifications are created in-app on domain events and optionally fanned to email/SMS per user
preference, with retry and local capture (Mailpit). The error envelope is uniform:
`{ "error": { "code", "message", "field?" } }` with codes 400/401/403/404/409/422/429/500; validation
uses Hibernate Validator with field-level messages surfaced in `error.field`. List endpoints page via
`?page=&size=` returning `{ items, page, size, total }`; default `size=20`, max `size=100`.

### 9.5 Observability, security, rate limiting & audit logging

SmallRye Health exposes `/q/health/live` and `/q/health/ready`; Micrometer/Prometheus exposes metrics
(request latency p95, payment success rate, transcode queue depth, payout volume); OpenTelemetry traces
span inbound → use case → outbound (DB, provider, storage). Security: TLS everywhere in prod, secrets
from env, no PII in logs, signed webhooks, CORS limited to the frontend origin. **Rate limiting** (per
account/IP, token-bucket; Redis-backed when present) protects auth, checkout, tip, play-record, and
upload endpoints, returning 429 with `Retry-After`. **Audit logging** (INV-10) appends an immutable
`AuditEntry` for every privileged mutation, queryable at `/admin/audit`.

---

## 10. Non-functional requirements

**Performance.** Read endpoints (catalog, store, search) target p95 ≤ 200 ms (the Admin health budget
is 200 ms); stream-URL issuance p95 ≤ 150 ms; checkout initiation p95 ≤ 500 ms (excluding provider
latency, which is async). **Scalability within a monolith.** Stateless app instances scale
horizontally behind a load balancer; Postgres is the shared store; heavy media transcoding runs out of
the request path (worker/queue); analytics served from rollups, not raw events. **Data integrity &
consistency.** Money and ownership operations are transactional and idempotent; the ledger is always
balanced (INV-6); ownership is never granted without settlement (INV-1) and is revoked on refund
(INV-9); scheduled go-live is exactly-once (INV-7). **Availability.** Target 99.9% for read/stream
paths; payments degrade gracefully (queue + reconcile) when a provider is slow; `maintenanceMode`
returns 503 for non-admin writes while keeping reads available where safe. **Compliance.** PII
minimization and encryption at rest/in transit; DSAR export/delete and Takedown/Tax workflows
(§6.12.9) and audit retention align with the **Ghana Data Protection Act, 2012 (Act 843)**; payment
handling follows provider/PCI obligations (cards tokenized via the gateway; no PAN stored). KYC data is
access-restricted and retained only as long as required for payout compliance.

---

## 11. Build sequencing / roadmap

Dependency-ordered phases (detail in §8.1):

1. **Phase 0 — Foundations.** Platform kernel (money/error/pagination/config/flags), scheduler, media
   pipeline, audit writer. *Exit:* health green under Compose; ArchUnit + contract test harness in CI.
2. **Phase 1 — Identity & Catalog.** Accounts/auth/RBAC; public catalog + search; release wizard +
   state machine. *Exit:* a fan can browse; an artist can submit a release that an admin can approve.
3. **Phase 2 — Commerce & Payments core.** Cart, payment intents + webhooks, ledger, checkout →
   ownership grant, ownership-aware streaming. *Exit:* a fan buys a track via (sandbox) MoMo and the
   30s preview unlocks to full on settlement; creator balance accrues 70%.
4. **Phase 3 — Money completion.** Payout methods, KYC-gated withdrawals, weekly/single payout runs,
   refunds/chargebacks/disputes with ownership revocation. *Exit:* finance can run payouts and
   adjudicate a dispute end-to-end.
5. **Phase 4 — Surfaces & proposals.** Store, podcasts + tipping, events/ticketing, notifications
   (in-app + email/SMS), studio profile/podcasts/insights/settings, full admin console, analytics
   rollups, search reindex. *Exit:* the frontend runs against the API with all mocks removed and **no
   visual change**; full audit coverage.

---

## 12. Open questions / discrepancies

Each carries a recommended default so no work unit is blocked; revisit before the relevant phase.

- **OQ-1 — Admin sub-scope granularity for verify/data-export/compliance/audit-read.** The contract
  lists five roles but not the exact action→role map for a few operations. *Default:* verify →
  moderator+super-admin; data-export & compliance → super-admin (support may initiate, super-admin
  approves); audit read → super-admin. Make the map config-driven so it can be tuned without code.
- **OQ-2 — Tip fee percentage.** Payouts data implies tips net 90% (10% fee) vs the general 70/30.
  *Default:* tips 10% fee, sales/royalties 30%; both sourced from `PlatformSettings` (add `tipFeePct`).
- **OQ-3 — Token model.** JWT only vs JWT + refresh tokens / OIDC provider. *Default:* short-lived
  access JWT (SmallRye JWT) with re-login for v1; add refresh tokens in a later iteration; OIDC
  optional for social federation.
- **OQ-4 — Royalty model.** The UI shows "stream royalty" payout lines, but buy-to-own implies sales,
  not per-stream micro-royalties. *Default:* treat "royalty" as periodic accrual from platform-funded
  pools/owned-play bonuses (configurable, may be ₵0 initially); document precisely before WU-PAY-3.
- **OQ-5 — Pricing constraints.** `PRICE_OPTIONS = [2, 2.5, 3, 0]` are UI presets. *Default:* accept
  any `price ≥ 0` server-side (presets are UI hints); enforce a configurable max.
- **OQ-6 — Podcast preview window.** Music previews are 30s; podcasts have no explicit preview length.
  *Default:* reuse 30s for premium episode previews; make per-content-type configurable.
- **OQ-7 — `takedown` as a release status.** Mock enum lacks `takedown`; admin actions imply it.
  *Default:* add `takedown` to `ReleaseStatus` (kept distinct from `draft`).
- **OQ-8 — Deleting published content with owners.** Buy-to-own means owners must retain access.
  *Default:* deleting/taking-down a release **unlists** it but preserves existing owners' access and
  downloads; hard delete is disallowed once any grant exists.
- **OQ-9 — Local SMS capture.** No standard MailHog-equivalent for SMS. *Default:* a tiny in-repo SMS
  capture stub service (HTTP endpoint + UI) in Compose; the `SmsSender` adapter points at it in dev.
- **OQ-10 — Resumable uploads.** Multipart vs resumable (tus) for large WAV/FLAC. *Default:* plain
  multipart for v1 with a generous size limit; add resumable (tus or S3 multipart) when needed.
- **OQ-11 — Inventory/concurrency for tickets & limited exclusives.** Overselling risk under
  concurrency. *Default:* decrement availability inside the settlement transaction with row locking;
  reserve on checkout-initiate with a short TTL hold (revisit before WU-EVT/COM).
- **OQ-12 — Search backend.** Postgres FTS/`pg_trgm` vs OpenSearch. *Default:* Postgres `pg_trgm` for
  v1 behind the `SearchIndex` port; swap to OpenSearch when scale demands, no contract change.
