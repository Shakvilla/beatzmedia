# BeatzClik Current-State Feature Integration Assessment

**Date:** 2026-11-01  
**Purpose:** Establish the product and technical baseline before converting the Spotify/Audiomack strategy into an implementation backlog.  
**Compared against:** `2026-10-31-balanced-marketplace-feature-strategy-design.md`  
**Assessment basis:** The current working tree, not only the last commit. Uncommitted and untracked work is classified separately from completed capability.

## 1. Executive conclusion

BeatzClik does not need a platform rewrite to pursue the recommended ownership-first marketplace strategy. Its Quarkus modular monolith, hexagonal module boundaries, PostgreSQL schema, settlement-authoritative commerce, media pipeline, catalog, library, Studio, administration, and event-driven projections provide a credible foundation.

The main gap is not the number of screens or backend modules. It is the absence of a dependable listener-engagement spine connecting playback, discovery, following, campaigns, notifications, and creator analytics. The current system is strongest at publishing and transactions; it is weakest at reliable mobile listening, behavioral intelligence, fan relationship activation, and growth loops.

The strategy should therefore fit into the existing system through:

1. stabilization of the current provider-integration branch and repository state;
2. correction of mobile and API-contract failures;
3. extension of existing transactional modules rather than replacement;
4. three new bounded capabilities—`engagement`, `discovery`, and `campaigns`;
5. a later `community` capability after moderation, privacy, and notification foundations exist;
6. durable event delivery before release fanout and behavioral data become product-critical.

The recommended backlog should not start with recommendations, comments, AI, or broad new UI. It should start by making the current listening and release-to-fan journey truthful, measurable, and recoverable.

## 2. What was inspected

The review covered:

- the backend module/package structure, REST resources, ports, domain events, persistence entities, migrations, and architecture documents;
- the React web routes, query layer, Studio, Admin, player, support, library, and test/build configuration;
- the Flutter mobile structure, API calls, player, download flow, CI configuration, analyzer state, and test inventory;
- Git state and the active provider-selection, object-storage, email, payment, and runtime-configuration work;
- CI and release workflows;
- the Spotify assessment, Audiomack assessment, and validated marketplace strategy.

This is an integration assessment, not an implementation backlog. It identifies where work belongs and what must be true before backlog decomposition.

## 3. Repository and delivery state

### 3.1 The working tree is a transition state

The current repository is substantially ahead of `HEAD`, but the changes are not yet a stable baseline:

- 75 tracked/index entries are modified;
- 76 untracked entries are present;
- the unstaged diff reports 9,506 insertions and 197 deletions across 75 files;
- the complete `mobile/` application remains untracked;
- provider-selection migrations and implementation are untracked or uncommitted;
- large error-log artifacts are present in the change set;
- six provider-integration work units are marked `in_progress`, with three more still `todo`.

The active provider work includes:

- audited provider selection and runtime configuration;
- payment gateway pinning and safe-disable behavior;
- MinIO/MEGA S4 routing and durable object-store identity;
- Resend/SMTP selection and delivery identifiers;
- password-reset email through the selected transport;
- corresponding Admin settings UI and migrations through V986.

This work compiles and the current migration set applies to an empty PostgreSQL database, but the backlog itself says credentialed checks, migration tooling, integration verification, and rollout evidence remain. It must be stabilized before unrelated feature work is layered on top.

### 3.2 Current verification snapshot

| Area | Result | Meaning |
|---|---|---|
| Backend compile | Pass | All 1,323 main Java sources compile with the provider work present. |
| Database migrations | Pass in current test boot | Flyway validated and applied 70 migrations through V986 to an empty database. |
| Backend tests | Pass, but slow/mixed | 1,512 tests passed with no failures in about 10 minutes. The command boots PostgreSQL and MinIO and mixes contract/integration tests into `mvn test`. |
| Backend test structure | Misclassified | Ordinary `mvn test` launches PostgreSQL/MinIO Testcontainers and contract-style Quarkus tests; the fast unit boundary is not fast or isolated. |
| Dedicated contract gate | Vacuous | No test is tagged `@Tag("contract")`, while CI selects `groups=contract`. |
| Web production build | Pass | TypeScript and Vite build complete. The main JS bundle is about 1.11 MB minified / 279 KB gzip and triggers a chunk-size warning. |
| Web tests | Three failures under required Node 22 | 348/351 passed. Two Studio release-detail tests fail because `wire.tracks` is assumed present; one unavailable-player recovery test times out. Node 25 additionally causes unrelated `localStorage` shim failures, confirming the need to enforce Node 22 locally. |
| Mobile analyzer | Fail | 44 analyzer findings, including unused/dead fields, removed lint rules, async-context warnings, and the unused download URL. |
| Mobile tests | Insufficient | Only one widget smoke test exists; the local run did not produce reliable feature validation. |
| Mobile CI | Would block | CI runs formatting, analyzer, tests, and an APK build; the analyzer currently exits non-zero. |

### 3.3 CI and release risks

- The contract-test job can succeed without executing contract tests.
- The `unit-tests` job invokes Quarkus/Testcontainers tests because test naming/tagging does not enforce the documented boundary.
- The release workflow waits for the single `build` check, not the complete CI workflow or all required checks.
- The compose smoke job still treats the catalog endpoint as optional even though catalog has long since shipped.
- Mobile is defined in CI, but because the entire app is untracked, the workflow and repository baseline are not aligned.
- CORS allows `GET,POST,PATCH,DELETE,OPTIONS` but omits `PUT`, although library mutations and several other APIs use `PUT`.
- Production-facing configuration still contains development fallbacks for database credentials, object-store credentials, JWT keys, and the payment webhook secret. Production startup should fail closed when required secrets are absent.
- Quarkus reports `quarkus.http.cors` as an unrecognized configuration key during current test boot, so the effective CORS configuration must be verified against the installed Quarkus version rather than assumed from the properties file.

## 4. Current product capability map

### 4.1 Strong foundations to preserve

| Capability | Current state | Strategic use |
|---|---|---|
| Catalog and release publishing | Strong | Reuse release drafts, track upload, moderation, scheduling, go-live, metadata, credits, downloadable policy, and public projections. |
| Settlement-authoritative commerce | Strong | Keep payment intent, idempotency, settlement, ownership grant/revocation, refunds, disputes, ledger, balances, and audit as the only authority for paid access. |
| Collaborator economics | Strong foundation | Reuse split persistence, invitations, ledger posting, payouts, and statements for the ownership-first differentiator. |
| Media pipeline | Strong but changing | Extend upload/transcode/signed delivery; finish provider migration and add resumable upload/download metadata rather than creating a second media system. |
| Library primitives | Good baseline | Likes, artist/show/playlist follows, saved albums, owned/downloadable tracks, and user playlists already exist. Extend these rather than duplicating them in engagement. |
| Search | Good baseline | Reuse catalog search projection; discovery ranking should not replace text search. |
| Admin and audit | Broad baseline | Reuse moderation, editorial operations, risk, support, compliance, taxonomy, finance, and audit surfaces. |
| Events and store | Useful differentiators | Connect existing events, tickets, beats, merchandise, and exclusives to discovery and campaigns. |

### 4.2 Partial capabilities

| Capability | What exists | What prevents strategy delivery |
|---|---|---|
| Playback | Ownership-aware signed stream, preview/full decision, play recording, basic web/mobile player | No reliable background media session; no persistent server/client queue; no cross-device position; play recording occurs at start and lacks progress/completion/skip/repeat semantics. |
| Offline | Entitlement and signed lossless download URL | Mobile never transfers or validates the file; it marks completion after obtaining a URL. No manifest, resume, removal, integrity, storage control, or offline playback path. |
| Home/charts | Catalog rails ordered by global plays, followers, or recency | “Made for you” is not personalized; Top/Trending/Emerging semantics and abuse-resistant ranking are absent. |
| Notifications | Durable in-app rows, email/SMS attempts, retry, dedupe key | Only tip notification is wired. No push channel, device tokens, follower release/episode fanout, quiet hours, deep-link analytics, or conversion attribution. |
| Creator analytics | Plays, sales, follows, and tips roll into aggregate Studio views | Completion, skips, repeat, unique listeners, sessions, top tracks, sources, geography, superfans, and funnel metrics are honest zeros/empty lists because no backing facts exist. |
| Verification | Admin can set an artist badge; payments has KYC records | No creator application, evidence, decision reasons, appeal/revocation, rights verification, or distinct identity/rights/monetization/trust statuses. Studio currently returns several fixed false values. |
| Editorial | Admin can manage featured slots, push items, and curated playlists | Creators cannot submit pitches; there is no eligibility, focus-track/story/campaign data, status, feedback, or separation workflow between pitch and moderation. |
| Support | Payment and ledger path for tips exists; podcast support is implemented | Artist-page support UI explicitly says it is not wired. No generalized support target, privacy choices, badges, acknowledgements, or supporter relationship view. |
| Playlists | User playlist creation and track membership | No reorder, descriptions, artwork, folders, collaboration, roles, history, import, or suggestions. |
| Creator mobile access | Studio exists on web | No validated responsive/installable Studio PWA and no dedicated creator client. Mobile is fan-only and immature. |

### 4.3 Missing bounded capabilities

| Capability | Current evidence | Consequence |
|---|---|---|
| Listener engagement/history | `play_event` is an append-only counted play with account, track, time, mode, and source only | Recently Played, continuation, completion, skip, repeat, retention, recommendation input, and accurate engagement analytics cannot be built honestly from current data. |
| Recommendation/ranking | No recommendation snapshots, reasons, rank versions, experiment IDs, or negative feedback | Personalization and radio would otherwise become ad hoc catalog queries that cannot be evaluated or explained. |
| Campaigns/attribution | No campaign, link, touch, pre-save, pitch, promo asset, or conversion model | Promotion, editorial submission, release-day journeys, QR/deep-link tracking, and creator conversion analytics lack a shared home. |
| Community | No comments, replies, reposts, public fan profiles, blocks, or creator broadcasts | Community work requires a new moderated boundary and must not be squeezed into library or notifications. |
| Durable domain-event delivery | Current code uses in-process CDI events; no implemented outbox/inbox was found despite documentation references | A crash can lose post-commit projections or fanout. This is unacceptable once release notifications, engagement facts, or campaign conversions depend on those events. |

## 5. Mobile is the immediate product bottleneck

The mobile application visually covers authentication, discovery, charts, player, library, downloads, commerce, podcasts, store, notifications, settings, and gap screens. That breadth overstates its delivery maturity.

Confirmed functional gaps include:

- likes, artist follows, and album saves use `POST`, while the backend contract requires `PUT`;
- the like removal path performs an unnecessary `POST` before `DELETE`;
- playlist track addition lacks a matching working client call;
- offline download reads `downloadUrl`, never uses it, and immediately records `completed`;
- `audio_service` is declared but unused, so lock-screen/background media controls are absent;
- the queue exists only in volatile controller state and lacks reorder, add-next, persistence, or restoration;
- stream expiry refresh can replace the source, but there is no background lifecycle or duplicate-play test matrix;
- Wi-Fi-only download is a fixed switch with no behavior;
- there is one test for the entire application;
- the entire mobile tree is untracked and the analyzer fails.

This means “background audio,” “real downloads,” “queue,” and “library” should be treated as completion of the current mobile product—not as optional feature expansion.

## 6. Recommended capability ownership

### 6.1 Extend existing modules

| Existing module | Keep ownership of | Add for the strategy | Do not put here |
|---|---|---|---|
| `playback` | Stream authorization, preview/full resolution, counted financial/platform plays | Account-scoped playback session, queue snapshot/position for cross-device continuity, signed URL renewal support | Recommendation profiles, creator analytics rollups, or social activity |
| `library` | Likes, follows, saves, ownership collection, user playlists | Library search/sort, playlist metadata/reorder/collaboration, follower lookup input ports, privacy-safe relationship events | Listening history or recommendation ranking |
| `catalog` | Artists, tracks, albums, releases, credits, rights declarations, publication | Rights status on releases, explicit metadata, chart-eligible metadata, campaign-facing release views | Charts, personalized feeds, or campaign attribution |
| `media` | Upload, validation, transcode, object storage, signed delivery | Resumable upload sessions, download checksum/size/format metadata, quality profiles | Device download manifests or account playback history |
| `notifications` | In-app notification and outbound delivery attempts | Push channel, device registrations, preferences, quiet hours, fanout jobs, open/delivery tracking, deep links | Audience segmentation truth or campaign conversion truth |
| `identity` | Account, authentication, roles, artist badge | Creator identity-verification application and decisions, 2FA/session controls later | Release rights verification or payout KYC |
| `payments` / `commerce` | Charges, settlement, ledger, ownership, refunds, payout KYC | General support targets, supporter privacy snapshot, collaborator statements | Optimistic entitlement or behavioral estimates |
| `analytics` | Creator/admin aggregate projections | Privacy-safe aggregates sourced from engagement/campaign events; settled finance remains sourced from ledger facts | Raw listener history, recommendation serving, or campaign workflow |
| `admin` | Operator UI/use cases for moderation, editorial, risk, compliance | Review adapters for verification, pitches, campaigns, comments, and abuse through module input ports | Direct reads of new modules’ tables |
| `studio` | Creator-facing composition surface | Mobile-responsive/PWA flows that compose catalog, campaigns, analytics, media, payments, and identity ports | Ownership of those underlying domains |

### 6.2 Add an `engagement` bounded context in Phase 1

`engagement` should own privacy-controlled, account-scoped behavioral facts and projections:

- playback session start, qualified play, progress, completion, skip, repeat, and duration;
- Recently Played and Continue Listening;
- referral source, campaign, discovery surface, device class, and network class;
- behavioral facts derived from likes, follows, saves, playlist adds, downloads, purchases, support, and event interest;
- retention/deletion controls;
- fan/creator relationship scores or segments as derived projections, never as financial authority.

It must not replace `playback.play_event`. The existing counted play remains the platform/anti-inflation fact. Engagement events answer product questions and may be updated or retained under privacy policy; financial and entitlement decisions must not depend on them.

### 6.3 Add a `discovery` bounded context in Phase 1

`discovery` should own generated and versioned discovery outputs:

- Top, Trending, Emerging, Genre, Regional, Following, and Recently Played feeds;
- rule-based recommendations and artist/track radio;
- rank score components, reason, source, ranking version, and experiment ID;
- negative feedback, block/exclusion inputs, and eligibility filters;
- later model ranking without changing catalog ownership.

It should consume catalog metadata and engagement projections through ports/events, store its own snapshots, and expose read APIs to web/mobile. It should not query `analytics` rollup tables or mutate catalog.

### 6.4 Add a `campaigns` bounded context in Phase 1

`campaigns` should connect creator release growth workflows:

- editorial pitches and their creator-visible status;
- canonical campaign links, QR codes, and source attribution;
- generated promotional assets;
- release notification campaigns;
- later pre-save, countdown, pre-purchase, early access, and sponsored placement;
- impression/touch/conversion facts that feed analytics.

Editorial decisions should be made through Admin but owned by a campaigns/editorial input port. Moderation approval remains in the catalog/admin moderation workflow. Paid promotion must use distinct models and surfaces from organic editorial selection.

### 6.5 Add `community` only in Phase 2

`community` should eventually own comments/replies, repost/recommend actions, public fan participation, blocks, reports, broadcast policy, and moderation history. It should reuse:

- library relationships for follows/playlists;
- notification delivery;
- identity/privacy settings;
- admin moderation and audit adapters;
- campaign attribution for broadcasts.

Collaborative playlist membership can remain library-owned because it changes playlist authorization and ordering. Community may project public activity from library events.

## 7. Required integration flows

### 7.1 Playback-to-engagement

1. Client requests an authorized stream from `playback`.
2. Client opens or restores an account playback session.
3. Client emits bounded progress checkpoints and terminal signals to `engagement`.
4. `playback` independently records a counted play under its anti-inflation rules.
5. Engagement produces history/continuation and privacy-safe behavioral facts.
6. Discovery consumes those facts for ranking.
7. Analytics consumes qualified aggregate facts for creator metrics.

This separation prevents a seek, resume, offline play, or recommendation event from corrupting royalty/commerce semantics.

### 7.2 Release-to-fan

1. `catalog` owns draft, media attachment, rights declaration, splits, moderation, and publication.
2. `campaigns` owns an optional pitch and campaign assets before publication.
3. A durable `ReleaseWentLive` event is recorded atomically with publication.
4. `discovery` updates eligible chart/recommendation projections.
5. `notifications` resolves followers through a library input port, creates idempotent fanout jobs, and sends allowed in-app/push/email messages.
6. Campaign source/deep-link data flows into engagement/campaign conversion facts.
7. Purchase/support remains settlement-authoritative in payments/commerce.
8. Analytics combines behavioral aggregates with separate settled ledger aggregates.

### 7.3 Offline owned-media flow

1. Client confirms owned/downloadable state from library/commerce projection.
2. Playback/media returns URL plus checksum, size, format, and expiry.
3. Mobile streams to a temporary account-scoped app-private file.
4. Expired URLs are renewed while preserving byte-range progress.
5. Size/checksum are verified.
6. The local manifest is atomically updated only after verification.
7. Player prefers a complete authorized local file, then full stream, then preview, then unavailable.
8. Sign-out/account switch makes another account’s files inaccessible; revocation policy is enforced on the next license check.

## 8. Cross-cutting changes required before feature scale

### 8.1 Durable events

Implement a PostgreSQL transactional outbox and idempotent consumer/inbox pattern inside the modular monolith before relying on events for:

- engagement facts;
- release/episode notification fanout;
- campaign conversions;
- discovery index updates;
- ownership/support relationship projections.

The system can continue using CDI for immediate local dispatch, but durable records must permit retry and replay after process failure.

### 8.2 Privacy and consent

Define retention, deletion/export, opt-out, and minimization rules before collecting fine-grained listening behavior, location, device, supporter identity, or public fan activity. Raw history and creator analytics must have different access policies.

### 8.3 Idempotency and event identity

Every cross-module event needs a stable event ID, occurred-at timestamp, schema version, causation/correlation IDs, and natural dedupe key. Current records such as `PlayRecorded` and `Followed` are useful seeds but are not a complete durable-event envelope.

### 8.4 Fanout and scheduling

Release notifications and creator broadcasts need paged audience resolution, persistent jobs, retry/backoff, per-recipient dedupe, provider throttling, preferences, and quiet hours. A synchronous observer must never loop over a large follower set in the release transaction path.

### 8.5 Contract and observability gates

Before new APIs multiply:

- make contract CI fail when zero tests execute;
- align test names/tags with unit vs integration phases;
- add consumer contract tests for web and mobile clients;
- add end-to-end release-to-fan and owned-download journeys;
- expose real queue depth, fanout failures, delivery rates, media migration state, and event lag;
- make production configuration fail closed.

## 9. What should happen before backlog decomposition

### Gate A — establish a trustworthy baseline

- finish, split, or explicitly park WU-EXT-1 through WU-EXT-6;
- remove generated/error artifacts from the proposed change set;
- commit the intended mobile baseline or exclude it from CI until it is ready;
- obtain a clean, reviewable branch with migrations and provider work verified;
- resolve documentation/code drift around the transactional outbox.

### Gate B — restore delivery truth

- correct mobile `PUT` mutations;
- implement real download transfer or remove the completed claim;
- integrate background audio service;
- make CORS support actual API verbs under the current Quarkus configuration;
- repair the Studio release mapper tests and player recovery timeout;
- enforce Node 22 in local/CI tooling;
- fix contract-test selection and release gating;
- remove production secret fallbacks.

### Gate C — define the enabling architecture contracts

Before individual feature stories are written, approve compact designs for:

- engagement event taxonomy and privacy/retention;
- durable outbox/inbox and replay;
- discovery snapshot/ranking contract;
- campaigns, attribution, and editorial separation;
- notification device/fanout model;
- account-scoped playback session and offline manifest.

These are not invitations to build frameworks in advance. Each should be delivered through the first vertical slice that needs it.

## 10. Recommended delivery slices after the gates

The future implementation backlog should be decomposed in this order:

1. **Baseline stabilization:** provider work, clean repository, CI truth, secret/CORS corrections.
2. **Reliable mobile listening:** background session, real owned downloads, persistent queue, contract-correct library.
3. **Engagement spine:** history, progress, completion/skip/repeat, privacy controls, durable delivery.
4. **Following loop:** followed-artist/show feeds, release/episode notifications, device push, preferences.
5. **Creator insight loop:** qualified engagement and conversion analytics backed by real events.
6. **Editorial and campaign loop:** creator pitch, admin review, campaign link/QR, promo assets, attribution.
7. **Support loop:** artist and release support, privacy options, supporter relationship projection, acknowledgement.
8. **Rule-based discovery:** honest Top/Trending/Emerging/Regional charts, explanations, radio, controls.
9. **Verification and mobile Studio:** identity/rights states, application/evidence/appeal, responsive PWA, resumable upload.
10. **Phase 2 community:** comments, replies, collaboration, broadcasts, public participation, and campaigns after safeguards exist.

The first end-to-end product milestone should be:

> A creator publishes and optionally pitches a release; a follower receives it, listens reliably in the background, purchases/supports it, downloads a verified file, and produces accurate engagement plus settled financial analytics.

That milestone joins existing strengths with the missing engagement and growth spine. It is a better readiness test than counting screens or endpoints.

## 11. Final assessment

BeatzClik is architecturally capable of supporting the recommended strategy. The modular monolith should be retained. The strongest path is to extend its transactional core with a small number of well-owned growth capabilities, not to imitate Spotify or Audiomack module-for-module.

However, the current project is not yet a safe input to a large feature backlog. The provider work is unfinished, mobile is not a committed or reliable product baseline, several CI guarantees are weaker than their names suggest, and the system lacks durable listener behavior and event delivery. Backlog decomposition should begin only after those facts are reflected as explicit foundation work and ownership boundaries are accepted.

The practical organizing principle is:

`stabilize → make listening truthful → capture engagement → activate followers → measure conversion → add discovery/community depth`
