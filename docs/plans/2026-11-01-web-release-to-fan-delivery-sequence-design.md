# BeatzClik Web Release-to-Fan Delivery Sequence

**Date:** 2026-11-01  
**Status:** Validated design  
**Scope:** Existing web application; music releases/tracks and podcasts/episodes  
**Deferred:** Mobile delivery, events, merchandise, beats, community features, and advanced/AI discovery

## 1. Objective

Optimize the existing BeatzClik web release-to-fan journey before expanding the product into broader community or mobile work.

The target journey is:

> A creator publishes and optionally pitches a music release or podcast launch; a follower discovers and receives it, listens through an honest web player, purchases or supports eligible content, and produces accurate engagement plus settled financial reporting.

The delivery sequence must improve the live product at every stage while building only the foundations required by the next usable increment.

## 2. Scope decisions

### 2.1 Included content

The first delivery sequence includes:

- artists, releases, and tracks;
- podcast shows and episodes;
- follows and follower feeds;
- web playback and listening engagement;
- in-app and email notifications;
- web discovery and attribution;
- purchases and support;
- creator analytics, editorial pitches, and promotion.

### 2.2 Deferred content and surfaces

The following are explicitly deferred:

- Flutter mobile reliability and feature work;
- events and tickets;
- merchandise, beats, licenses, and exclusives;
- comments, replies, reposts, public fan profiles, and broadcasts;
- collaborative playlists;
- video and creator Clips;
- conversational or model-ranked recommendations.

The architecture must permit these additions later without requiring them in the first sequence.

## 3. Architectural approach

Use a unified audience journey with content-specific publishing adapters.

Music and podcasts retain their existing domain ownership:

- `catalog` owns artists, releases, tracks, rights declarations, moderation state, scheduling, and music publication;
- `studio/podcasts` owns shows, episodes, scheduling, and episode publication.

Shared growth capabilities operate on primitive content references such as:

- `contentType`;
- `contentId`;
- `creatorId`;
- `publishedAt`.

Each consuming module maps these values into its own domain model. Music and podcast aggregates must not be collapsed into a shared publishing model.

### 3.1 Existing module ownership

| Module | Retained authority | Required extension |
|---|---|---|
| `catalog` | Music metadata, releases, tracks, rights, publication | Durable publication event and campaign-facing release view |
| `studio/podcasts` | Shows, episodes, scheduling, publication | Durable episode publication event |
| `library` | Follows, likes, saves, playlists, owned collection | Paged follower-resolution ports and durable relationship events |
| `playback` | Stream authorization, preview/full resolution, counted plays | Integration with account playback sessions while preserving counted-play separation |
| `notifications` | In-app notification and outbound attempts | Publication fanout, preferences, deep links, and conversion events |
| `analytics` | Creator/admin aggregate projections | Qualified engagement and attribution aggregates |
| `payments` and `commerce` | Settlement, ledger, ownership, refunds, payouts | Generalized support targets and privacy snapshot |
| `admin` | Moderation, editorial operations, risk, audit | Review surfaces through module input ports |
| `studio` | Creator-facing composition | Pitch, campaign, and analytics composition |

### 3.2 New capabilities

#### Engagement

`engagement` owns:

- account-scoped listening sessions;
- starts, qualified plays, progress, completion, skips, repeats, and duration;
- Recently Played and Continue Listening;
- referral, discovery surface, and campaign source;
- retention, deletion, and history opt-out;
- privacy-safe behavioral projections.

Engagement never grants ownership or calculates settled revenue. The existing playback play counter remains independent.

#### Discovery

`discovery` owns:

- Following, Top, Trending, Emerging, Genre, Ghana, and regional collections;
- rule-based recommendations and later radio queues;
- ranking scores, reasons, versions, and experiment identifiers;
- negative feedback and creator blocking;
- separation of organic, editorial, and paid surfaces.

#### Campaigns

`campaigns` owns:

- creator editorial pitches;
- campaign links and QR codes;
- source and conversion attribution;
- promotional assets;
- creator-visible pitch and campaign status;
- later pre-save, countdown, early-access, and sponsored-placement workflows.

## 4. Durable event delivery

### 4.1 Platform outbox/inbox

`platform` owns generic outbox and inbox infrastructure only. It does not own music or podcast concepts.

The event envelope contains:

- stable event ID;
- event type;
- schema version;
- producer;
- occurred-at timestamp;
- correlation ID;
- causation ID;
- serialized payload;
- attempt count, next attempt, and delivery state.

Publication state and its outbox event must be written in the same PostgreSQL transaction. A successful publication can therefore never exist without a durable event available for retry.

### 4.2 Dispatch and consumption

A scheduled dispatcher:

1. claims pending events in bounded batches using row locking;
2. invokes registered in-process consumers;
3. records successful completion or retry state;
4. applies exponential backoff to retryable failures;
5. moves exhausted or non-retryable events to a visible failed state;
6. supports audited manual replay.

Consumers record the event ID in an inbox before applying changes. Repeated delivery becomes a safe no-op.

### 4.3 Fanout

Notification fanout never executes in the publication transaction.

For each publication:

1. notifications creates a persistent fanout job;
2. followers are resolved from library in pages;
3. the job maintains a resumable cursor;
4. each notification uses a unique event-recipient-type key;
5. outbound delivery attempts remain separate from the durable in-app notification;
6. aggregate delivery/open/conversion results are available without exposing private fan data.

## 5. Delivery sequence

### Stage 0 — Stabilize the delivery baseline

Purpose: establish a trustworthy branch, test boundary, and release process before adding new behavior.

Deliver:

- finish, merge, split, or explicitly park active provider work;
- remove generated/error artifacts from proposed commits;
- repair the three failing Node 22 web tests;
- make contract CI fail when zero tests execute;
- separate fast unit tests from Testcontainers/Quarkus integration tests;
- make release publishing wait for the complete required CI set;
- correct CORS configuration and support documented verbs;
- make production configuration fail closed when required secrets are absent;
- update compose smoke assertions for already-delivered catalog functionality.

Exit criteria:

- all 351 web tests pass under the pinned Node 22 runtime;
- backend unit, integration, contract, architecture, migration, and smoke jobs have honest boundaries;
- provider changes are reviewable and their migrations are verified;
- no release is published from a partially green commit.

### Stage 1 — Durable publication signals

Purpose: make release and episode publication recoverable and replayable.

Deliver:

- platform outbox/inbox persistence and scheduler;
- versioned durable `ReleaseWentLive` event;
- versioned durable `EpisodePublished` event;
- idempotent consumers and audited replay;
- operational metrics for pending, processing, failed, oldest-event age, retries, and throughput;
- migration of publication-dependent projections without changing public publication behavior.

Exit criteria:

- publication and event insertion are atomic;
- committed events survive process termination;
- duplicate delivery cannot create duplicate projections;
- one poison event cannot block the remaining batch;
- replay is safe, observable, and audited.

Required tests:

- transaction rollback;
- crash/restart recovery;
- duplicate delivery;
- concurrent dispatcher claims;
- poison-event isolation;
- event schema compatibility;
- replay idempotency.

### Stage 2 — Web listening and engagement

Purpose: create the behavioral foundation for history, discovery, notifications, and creator analytics.

Deliver:

- account-scoped web listening session;
- session ID and monotonic checkpoint sequence;
- start, qualified play, progress, completion, skip, repeat, and duration events;
- Recently Played web view;
- podcast Continue Listening and resume position;
- history clearing and future-history opt-out;
- privacy-safe engagement aggregates for analytics;
- documented anonymous-listening policy.

Exit criteria:

- duplicate or out-of-order checkpoints do not inflate metrics;
- financial counted plays remain separate;
- engagement cannot grant ownership or revenue;
- creators receive aggregates, never raw fan history;
- podcasts reliably resume from the last meaningful position.

### Stage 3 — Following and notification loop

Purpose: connect publication to existing fan relationships.

Deliver:

- paged artist and show follower-resolution ports;
- chronological Following feed for releases and episodes;
- publication-triggered in-app notifications;
- resumable fanout jobs;
- music, podcast, in-app, and email preferences;
- content-aware deep links;
- privacy-safe open and conversion facts;
- unavailable/removed content handling.

Exit criteria:

- fanout never delays or rolls back publication;
- recipient records are idempotent;
- zero-follower and large-audience publication both complete safely;
- preference changes, removed accounts, and removed content are respected;
- administrators see delivery health without receiving private follower data.

### Stage 4 — Discovery and conversion attribution

Purpose: replace misleading rails with explainable, measurable discovery.

Deliver:

- removal or renaming of non-personalized “Made for you” labels;
- defined Top, Trending, Emerging, Genre, Ghana, regional, music, and podcast collections;
- rule-based recommendations from follows, likes, saves, history, purchases, and metadata;
- stored ranking reason and version;
- hide-recommendation and block-creator controls;
- distinct organic, editorial, and paid placements;
- impression, open, start, purchase, and support attribution.

Exit criteria:

- Top and Trending have different documented formulas;
- Trending includes velocity, freshness, unique-listener, completion, save, and abuse signals;
- every recommendation is explainable;
- ranking changes are versioned, measurable, and reversible;
- attribution survives the complete web conversion journey.

### Stage 5 — Purchase and support completion

Purpose: connect listening and discovery to BeatzClik’s ownership and direct-support differentiators.

Deliver:

- web support for artist, release, track, podcast, and episode targets;
- authoritative creator/settlement-account resolution;
- independent fan identity and amount visibility choices;
- supporter relationship projections after settlement;
- privacy-safe supporter counts, repeat-supporter views, and aggregate value;
- refund and chargeback reversal of applicable projections;
- engagement conversion facts separate from ledger facts.

Exit criteria:

- pending payment never grants ownership, supporter status, or revenue;
- settlement creates balanced ledger entries exactly once;
- refund and chargeback behavior is consistent and audited;
- private supporter identity is not leaked;
- reported financial totals reconcile to the ledger.

### Stage 6 — Creator activation

Purpose: give creators a complete publish, pitch, promote, and measure loop.

Deliver:

- editorial pitches for eligible music releases and podcast launches;
- focus content, genre/category, mood, market, story, campaign plan, collaborators, requested surfaces, and explicit state;
- creator-visible pitch status and optional editorial feedback;
- separate moderation and editorial decisions;
- canonical campaign links and QR codes;
- promotional cards for publication, purchase, support, and milestones;
- qualified play, completion, skip, repeat, unique-listener, follow, save, purchase, support, source, campaign, and content timeline analytics;
- ledger-authoritative financial reporting beside behavioral analytics.

Exit criteria:

- moderation does not imply editorial selection;
- paid promotion cannot affect organic editorial state;
- campaign source survives to conversion reporting;
- creator analytics contain no fabricated or placeholder engagement fields;
- raw fan history and private identities are never exposed.

## 6. Core data flows

### 6.1 Publication-to-fan

1. Catalog or podcasts commits publication plus an outbox event.
2. Discovery creates or updates an eligible content projection.
3. Notifications creates a persistent fanout job.
4. Fanout pages through library followers and creates deduplicated notifications.
5. The fan opens a Following item or notification.
6. The discovery/notification source is attached to the engagement session.
7. Web playback sends engagement checkpoints.
8. Purchase or support enters existing commerce/payment processing.
9. Settlement creates ownership/support and ledger outcomes.
10. Analytics combines behavioral aggregates with separate settled finance aggregates.

### 6.2 Player-to-engagement

1. Playback returns an authorized stream or preview.
2. Web creates or restores a listening session.
3. The client sends bounded progress checkpoints.
4. Engagement validates account, session, content, and sequence.
5. Engagement derives qualified play, completion, skip, repeat, and duration.
6. Playback independently records its counted play under existing anti-inflation rules.
7. History and continuation projections update.
8. Discovery and analytics consume privacy-safe derived facts.

### 6.3 Support conversion

1. The fan chooses a supported content target.
2. The backend resolves creator and settlement account authoritatively.
3. Visibility preferences are captured with the support intent.
4. Payment remains pending until provider settlement.
5. Settlement posts balanced ledger entries exactly once.
6. A durable settled-support event updates engagement, supporter projection, notifications, and analytics.
7. Refund or chargeback emits a compensating durable event.

## 7. Error and recovery behavior

- Publication succeeds only with a committed outbox event.
- Event processing uses bounded retries and exponential backoff.
- Failed events are visible, diagnosable, and replayable.
- A poison event cannot block unrelated events.
- Fanout jobs maintain cursors and resume after failure.
- Notification-provider outages do not remove in-app notifications.
- Duplicate web checkpoints and domain events are idempotent.
- Out-of-order playback checkpoints cannot move continuation backward or inflate completion.
- Unavailable or removed content is represented honestly rather than silently disappearing from history.
- Pending or failed payments never appear as settled conversion.
- Refunds and chargebacks produce compensating projections without deleting audit history.

## 8. Privacy and security rules

- Raw account listening history is accessible only to the account and tightly scoped internal services.
- Creator analytics expose aggregates and approved supporter identity only.
- History collection supports clear, export/delete, and future opt-out behavior.
- Campaign attribution stores only the data needed to measure the journey.
- Device, network, geography, and referrer data use coarse categories where possible.
- Fan identity and support amount visibility are independent choices.
- Public and paid placement labels cannot be inferred solely from ranking position.
- Admin review actions, replays, and editorial decisions are audited.

## 9. Testing and release gates

Each stage requires:

- domain and application tests;
- API contract tests that fail when zero tests execute;
- PostgreSQL integration tests;
- web component/query tests;
- an automated end-to-end journey;
- accessibility validation for changed web flows;
- analytics-event and idempotency validation;
- authorization and privacy tests;
- load tests for fanout and ranked feeds when their scale warrants it.

The combined sequence release gate is one automated journey for both music and podcasts:

1. creator publishes content;
2. durable event dispatches;
3. follower receives feed entry and notification;
4. fan opens and listens;
5. engagement records qualified behavior;
6. fan purchases or supports;
7. settlement posts exactly once;
8. creator analytics show behavioral and settled financial outcomes;
9. no raw private fan data is exposed.

## 10. Success measures

Primary milestone measure:

- monthly active purchasing or supporting web fans with at least one qualified listening session.

Stage measures include:

- outbox oldest-event age and failed-event rate;
- publication-to-notification latency;
- Following-feed open rate;
- qualified-start and completion rate;
- podcast continuation rate;
- recommendation open and qualified-play rate;
- listening-to-purchase/support conversion;
- repeat-supporter rate;
- creator pitch submission and selection rate;
- percentage of analytics fields backed by real events;
- refund/chargeback reconciliation accuracy.

## 11. Sequencing rule

Do not begin a later stage merely because its UI is easy to build. A stage begins when the preceding stage’s authoritative data, failure behavior, privacy rules, and acceptance gates are operating reliably.

The delivery order is:

`stabilize → durable publication → engagement → following → discovery → purchase/support → creator activation`

This order optimizes the existing web product while creating the minimum reusable foundation for later mobile, events, store, community, and advanced discovery work.
