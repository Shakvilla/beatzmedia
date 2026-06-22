# Architecture Design Doc — `podcasts` (Podcasts)

> **Status:** Stable · **PRD source:** `BACKEND-PRD.md` §6.8 · **Owning context:** `podcasts` ·
> **Package root:** `org.shakvilla.beatzmedia.podcasts`
>
> This ADD is consumed by Claude Code agents. It is the design contract for the module: an agent
> reads it, plans the listed work units, implements within the stated ports/adapters, writes the
> tests, and opens a PR. Do not invent endpoints or fields not traceable to the PRD / `API-CONTRACT.md`.

## 1. Purpose & responsibilities

The `podcasts` module owns podcast **shows** and **episodes** and serves the Fan-facing podcasts
browse, show-detail, and gated-playback experience, plus **instant MoMo tipping** of a show. It
mirrors the music buy-to-own model: a free feed for reach, plus **premium** (buy-to-own) and
**early-access** episodes, an optional **season pass**, and tips credited 90% to the creator.

It owns the `podcast` and `podcast_episode` tables and the rules that decorate episodes with
per-caller ownership and early-access state. It explicitly **does not own**: creator-side show/episode
authoring (that is `studio`, WU-STU-2), media upload/transcode/streaming (`media`, `MediaService`),
ownership grants and the season-pass expansion (`commerce`, INV-2 — referenced in §9), the ledger and
the 90/10 tip split posting (`payments`, `IssueTip`, WU-PAY-3), and notification delivery
(`notifications`, which consumes `TipReceived`). Persistence is never shared across modules.

**Surfaces:** Fan (podcasts list, show detail, support modal). **HLFRs covered:** HLFR-PODCAST-01
(browse & gated playback), HLFR-PODCAST-02 (tipping). **LLFRs:** PODCAST-01.1, 01.2, 01.3, 02.1.

## 2. Context & dependencies (C4 component view)

```mermaid
flowchart LR
  Fan([Fan client])
  subgraph podcasts[podcasts module]
    IN[REST adapter<br/>PodcastResource]
    APP[Use cases<br/>ListPodcasts · GetPodcast<br/>ListEpisodes · TipShow]
    DOM[Domain<br/>Podcast · PodcastEpisode<br/>access rules]
    OUT[Outbound adapters]
  end
  Fan --> IN --> APP --> DOM
  APP --> OUT
  OUT --> REPO[(Postgres<br/>podcast · podcast_episode)]
  APP --> OWN[OwnershipReader port]
  APP --> MED[MediaService port]
  APP --> TIP[IssueTipUseCase<br/>payments input port]
  APP --> EVT[EventPublisher port]
  OWN -.->|reads grants| COMMERCE[commerce module]
  MED -.->|signed/preview URLs| MEDIA[media module]
  TIP -.->|90/10 ledger| PAYMENTS[payments module]
  EVT -.->|TipReceived| NOTIF[notifications module]
```

**Dependency rule.** Hexagonal: `domain` depends on nothing; `application` depends on `domain` and on
ports it declares; inbound/outbound adapters depend inward only (ArchUnit-enforced). The module reaches
other modules **only** through input ports it calls (`payments.IssueTipUseCase`) and outbound ports it
defines (`OwnershipReader`, `MediaService`, `EventPublisher`); it holds **no** cross-module FKs and
references foreign aggregates by id. It **publishes** `TipReceived`; it consumes no events. Ownership
is resolved per-request via `OwnershipReader` (backed by `commerce` grants), never by joining tables.

## 3. Domain model

| Name | Kind | Key fields | Notes |
|---|---|---|---|
| `Podcast` | Aggregate root | `id`, `title`, `publisher`, `category`, `image`, `episodeCount`, `popularity`, `seasonPassPrice?`, `supportsTips` | Show; tipping allowed only when `supportsTips`. |
| `PodcastEpisode` | Entity (child of show) | `id`, `podcastId`, `title`, `image`, `durationSec`, `publishedAt`, `episodeNumber?`, `isPremium`, `price?`, `isEarlyAccess`, `publicAt?`, `mediaAssetId?` | Free / premium / early-access. |
| `EpisodeAccess` | Value object | `accessible`, `previewOnly`, `previewSec` | Computed per caller from premium/early-access + ownership. |
| `PodcastCategory` | Enum | — | See below. |
| `TipResult` | Value object | `status`, `tipId` | Returned by `TipShow`. |

**Enums** (lifted verbatim from `Frontend/src/types/index.ts` `PodcastCategory`):
`News & Politics | Comedy | Business | Sports | Culture | Tech | Health | Storytelling`.

**Invariants enforced here**
- **INV-3 (preview gate).** A premium episode the caller does not own yields full audio withheld;
  `MediaService` returns a **preview clip of `previewSec` (default 30, OQ-6)**, configurable per
  content-type via `PlatformSettings`. Free or owned → full audio.
- **Early-access guard.** An `isEarlyAccess` episode is locked until `publicAt`; before `publicAt`
  only owners get full audio (others get preview); at/after `publicAt` it is free to everyone.
- **INV-4 (tip split, referenced).** Tips credit creator 90% / platform 10%; the actual posting is in
  `payments` (`IssueTip`) — this module supplies amount + show/creator id and asserts `amount > 0`.

```mermaid
erDiagram
  PODCAST ||--o{ PODCAST_EPISODE : has
  PODCAST {
    text id PK
    text title
    text publisher
    text category
    text image
    text description
    int  episode_count
    int  popularity
    bigint season_pass_price_minor
    text season_pass_currency
    boolean supports_tips
    timestamptz created_at
  }
  PODCAST_EPISODE {
    text id PK
    text podcast_id FK
    text title
    text image
    text description
    int  duration_sec
    int  episode_number
    boolean is_premium
    bigint price_minor
    text price_currency
    boolean is_early_access
    timestamptz public_at
    text media_asset_id
    timestamptz published_at
    timestamptz created_at
  }
```

## 4. Application layer (ports)

### 4.1 Input ports (use cases)

```java
/** Lists shows, optionally filtered by category. LLFR-PODCAST-01.1. */
public interface ListPodcasts {
    Page<Podcast> list(Optional<PodcastCategory> category, PageRequest page);
}

/** Returns a single show. LLFR-PODCAST-01.2. */
public interface GetPodcast {
    Podcast get(PodcastId id);
}

/** Lists a show's episodes decorated with per-caller ownership/early-access state. LLFR-PODCAST-01.3. */
public interface ListEpisodes {
    List<EpisodeView> list(PodcastId id, Optional<AccountId> caller);
}

/** Issues an instant MoMo tip to a show, credited 90/10 via payments. LLFR-PODCAST-02.1. */
public interface TipShow {
    TipResult tip(PodcastId id, AccountId fan, Money amount,
                  PaymentMethodId method, IdempotencyKey key);
}
```

| Port | Trigger | Authorization | Idempotency | Events | LLFR |
|---|---|---|---|---|---|
| `ListPodcasts` | `GET /v1/podcasts` | Public | n/a (read) | — | 01.1 |
| `GetPodcast` | `GET /v1/podcasts/:id` | Public | n/a (read) | — | 01.2 |
| `ListEpisodes` | `GET /v1/podcasts/:id/episodes` | Public; ownership decoration requires auth | n/a (read) | — | 01.3 |
| `TipShow` | `POST /v1/podcasts/:id/tip` | `fan` (authenticated) | `IdempotencyKey` — same key → same `TipResult`, no double charge | `TipReceived` (AFTER_SUCCESS) | 02.1 |

### 4.2 Output ports

```java
/** Owned-table reads/writes for shows & episodes. Adapter: PodcastJpaRepository (Postgres). */
public interface PodcastRepository {
    Page<Podcast> findShows(Optional<PodcastCategory> category, PageRequest page);
    Optional<Podcast> findShow(PodcastId id);
    List<PodcastEpisode> findEpisodes(PodcastId id);
}

/** Per-caller ownership lookup. Adapter: CommerceOwnershipAdapter (calls commerce read port). */
public interface OwnershipReader {
    boolean ownsEpisode(AccountId caller, EpisodeId episode);
    Set<EpisodeId> ownedEpisodes(AccountId caller, Set<EpisodeId> candidates);
}

/** Streaming/preview URL issuance honouring INV-3. Adapter: MediaServiceAdapter (media module). */
public interface MediaService {
    StreamUrl fullStreamUrl(MediaAssetId asset);
    StreamUrl previewStreamUrl(MediaAssetId asset, int previewSec);
}

/** Publishes domain events after commit. Adapter: CdiEventPublisher / outbox. */
public interface EventPublisher {
    void publish(DomainEvent event);
}
```

```java
/** payments input port consumed by TipShow for the 90/10 split + ledger posting. */
public interface IssueTipUseCase {
    TipOutcome issueTip(AccountId fan, CreatorId creator, PodcastId show,
                        Money amount, PaymentMethodId method, IdempotencyKey key);
}
```

`Clock` and `IdGenerator` (kernel ports) supply `publicAt` comparison time and tip ids.

## 5. Adapters

### 5.1 Inbound — REST resources

Base path `/v1`. JSON, UTF-8. Money as `{ amount, currency }`; durations whole seconds; timestamps ISO-8601.

| Method | Path | Auth/scope | Request DTO | Response DTO | Success | Error codes | LLFR |
|---|---|---|---|---|---|---|---|
| GET | `/v1/podcasts?category=&page=&size=` | Public | — (query) | `Page<PodcastDto>` | 200 | `VALIDATION` (bad category/page) | 01.1 |
| GET | `/v1/podcasts/:id` | Public | — | `PodcastDto` | 200 | `NOT_FOUND` | 01.2 |
| GET | `/v1/podcasts/:id/episodes` | Public; Bearer optional (enables `isOwned`/full audio) | — | `PodcastEpisodeDto[]` | 200 | `NOT_FOUND` | 01.3 |
| POST | `/v1/podcasts/:id/tip` | `fan` (Bearer) | `TipRequest` + `Idempotency-Key` | `TipResponse` | 202 | `UNAUTHENTICATED`, `VALIDATION` (`amount<=0`), `TIPS_DISABLED`, `NOT_FOUND`, `PAYMENT_FAILED`, `IDEMPOTENCY_CONFLICT` | 02.1 |

Resources are thin: map DTO → command, call input port, map result → DTO. No business logic in
resources. The episodes endpoint extracts the caller from the bearer token if present; absent → all
episodes returned with `isOwned=false` and premium/early-access locked.

### 5.2 Outbound — persistence & integrations

- **`PodcastJpaRepository`** maps domain ↔ JPA entities (`PodcastEntity`, `PodcastEpisodeEntity`);
  domain objects carry no ORM annotations. Indexed reads by `category` and `podcast_id` (§7).
- **`CommerceOwnershipAdapter`** implements `OwnershipReader` by calling the `commerce` ownership read
  port (by id, no FK). Batches episode ids to avoid N+1.
- **`MediaServiceAdapter`** implements `MediaService` against the `media` module; issues signed full or
  `previewSec`-clipped URLs. The media layer also performs server-side preview clipping (INV-3).
- **`PaymentsTipAdapter`** wraps `payments.IssueTipUseCase` for `TipShow`.
- **`CdiEventPublisher`** publishes `TipReceived` via the transactional outbox (AFTER_SUCCESS).
- **Transaction boundary** = the use case (`@Transactional` on the application service impl). Tip
  posting itself commits within `payments`; this module's own writes (e.g. tip audit row, if any) and
  the `TipReceived` publish are outbox-coupled to that success.

## 6. DTOs & API shapes

Traceable to `Frontend/src/types/index.ts`. Money is `{ amount, currency: "GHS" }`; durations seconds;
timestamps ISO-8601.

**`PodcastDto`** (from `Podcast`):
`id`, `title`, `publisher`, `image`, `category`, `description?`, `episodeCount?`, `popularity?`,
`seasonPassPrice?: Money`, `supportsTips?: boolean`.

**`PodcastEpisodeDto`** (from `PodcastEpisode`):
`id`, `podcastId`, `title`, `showTitle`, `image`, `duration` (sec), `publishedAt` (ISO),
`description?`, `episodeNumber?`, `isPremium?: boolean`, `price?: Money`, `isOwned?: boolean`
(per caller via `OwnershipReader`), `isEarlyAccess?: boolean`, `publicAt?` (ISO).

**`TipRequest`** (from API-CONTRACT §8 `{ amount }` + idempotency): `amount: Money` (or
`{ amount: number }`, currency defaulted GHS), `paymentMethodId: ID`, `idempotencyKey: string` (or
`Idempotency-Key` header — header takes precedence).

**`TipResponse`**: `{ status: "ACCEPTED" | "PROCESSING" | "SETTLED", tipId: ID }` (HTTP 202).

`isOwned`, `price`, `isPremium`, `isEarlyAccess`, `publicAt`, `seasonPassPrice`, `supportsTips` are the
load-bearing gating/monetization fields; defaults: absent `isPremium`/`isEarlyAccess`/`isOwned` → false.

## 7. Persistence schema & migrations

```sql
CREATE TABLE podcast (
  id                       TEXT PRIMARY KEY,
  title                    TEXT        NOT NULL,
  publisher                TEXT        NOT NULL,
  image                    TEXT        NOT NULL,
  category                 TEXT        NOT NULL,
  description              TEXT,
  episode_count            INTEGER     NOT NULL DEFAULT 0,
  popularity               INTEGER     NOT NULL DEFAULT 0,
  season_pass_price_minor  BIGINT,                 -- pesewas; NULL = no season pass
  season_pass_currency     TEXT,                   -- 'GHS' when price set
  supports_tips            BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_pod_category CHECK (category IN
    ('News & Politics','Comedy','Business','Sports','Culture','Tech','Health','Storytelling')),
  CONSTRAINT chk_pod_season_pass CHECK (
    (season_pass_price_minor IS NULL AND season_pass_currency IS NULL) OR
    (season_pass_price_minor >= 0 AND season_pass_currency IS NOT NULL))
);
CREATE INDEX idx_podcast_category ON podcast (category);
CREATE INDEX idx_podcast_popularity ON podcast (popularity DESC);

CREATE TABLE podcast_episode (
  id              TEXT PRIMARY KEY,
  podcast_id      TEXT        NOT NULL REFERENCES podcast (id) ON DELETE CASCADE,
  title           TEXT        NOT NULL,
  image           TEXT        NOT NULL,
  description     TEXT,
  duration_sec    INTEGER     NOT NULL CHECK (duration_sec > 0),
  episode_number  INTEGER,
  is_premium      BOOLEAN     NOT NULL DEFAULT FALSE,
  price_minor     BIGINT,                            -- pesewas; required when premium/early-access
  price_currency  TEXT,
  is_early_access BOOLEAN     NOT NULL DEFAULT FALSE,
  public_at       TIMESTAMPTZ,                       -- when early-access becomes free
  media_asset_id  TEXT,                              -- id into media module (no FK)
  published_at    TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_ep_price CHECK (
    ((is_premium OR is_early_access) AND price_minor IS NOT NULL AND price_currency IS NOT NULL)
    OR (NOT is_premium AND NOT is_early_access)),
  CONSTRAINT chk_ep_early CHECK (NOT is_early_access OR public_at IS NOT NULL)
);
CREATE INDEX idx_episode_podcast ON podcast_episode (podcast_id, published_at DESC);
```

Money in **minor units** (`*_minor`, BIGINT pesewas); durations in **whole seconds** (`duration_sec`);
premium/early-access **flags** (`is_premium`, `is_early_access`); `public_at` TIMESTAMPTZ.

**Flyway list** (forward-only, `src/main/resources/db/migration/`):
- `V<n>__create_podcast.sql`
- `V<n+1>__create_podcast_episode.sql`
- `R__seed_dev_data.sql` (repeatable, dev/test) — contributes shows/episodes from
  `Frontend/src/lib/podcast-data.ts` (e.g. `sincerely-accra` free + premium + early-access feed).

## 8. Key flows

**(a) List episodes with ownership & early-access gating (LLFR-PODCAST-01.3)**

```mermaid
sequenceDiagram
  autonumber
  participant C as Fan client
  participant R as PodcastResource
  participant UC as ListEpisodes
  participant Repo as PodcastRepository
  participant Own as OwnershipReader
  participant Clk as Clock
  C->>R: GET /v1/podcasts/:id/episodes (Bearer optional)
  R->>UC: list(id, caller?)
  UC->>Repo: findShow(id) / findEpisodes(id)
  alt show unknown
    Repo-->>UC: empty
    UC-->>R: throw NotFound
    R-->>C: 404 NOT_FOUND
  end
  opt caller present
    UC->>Own: ownedEpisodes(caller, episodeIds)
    Own-->>UC: owned set
  end
  loop each episode
    UC->>Clk: now()
    Note over UC: isOwned = caller in owned set<br/>accessible = !isPremium OR isOwned OR (isEarlyAccess AND now>=publicAt)<br/>previewOnly = isPremium AND !isOwned AND !(now>=publicAt)
  end
  UC-->>R: EpisodeView[] (isOwned, isEarlyAccess, publicAt, previewOnly)
  R-->>C: 200 PodcastEpisodeDto[]
```

> Full audio is only ever fetched via `MediaService.fullStreamUrl` for accessible episodes; premium
> unowned episodes resolve to `previewStreamUrl(asset, previewSec=30)` (INV-3, OQ-6). The list
> endpoint returns metadata + flags; the actual audio URL is requested by the player per episode.

**(b) Tip → IssueTip (90/10) → TipReceived → notification (LLFR-PODCAST-02.1)**

```mermaid
sequenceDiagram
  autonumber
  participant C as Fan client
  participant R as PodcastResource
  participant UC as TipShow
  participant Repo as PodcastRepository
  participant Tip as payments.IssueTipUseCase
  participant Led as Ledger (payments)
  participant Pub as EventPublisher
  participant Not as notifications
  C->>R: POST /v1/podcasts/:id/tip { amount, paymentMethodId } + Idempotency-Key
  R->>UC: tip(id, fan, amount, method, key)
  UC->>UC: guard amount>0 else 422 VALIDATION
  UC->>Repo: findShow(id)
  alt unknown
    UC-->>R: NotFound → 404
  else tips disabled
    UC-->>R: TIPS_DISABLED → 403
  end
  UC->>Tip: issueTip(fan, creator, show, amount, method, key)
  Tip->>Led: post balanced rows — creator +90%, platform +10% (INV-4)
  Led-->>Tip: settled
  Tip-->>UC: TipOutcome(status, tipId)
  UC->>Pub: publish TipReceived{tipId, show, creator, amount, fan} (AFTER_SUCCESS)
  UC-->>R: TipResult
  R-->>C: 202 { status, tipId }
  Pub-->>Not: TipReceived → deliver 'tip' notification to creator
```

**Episode access state machine**

```mermaid
stateDiagram-v2
  [*] --> Free: not premium, not early-access
  [*] --> PremiumLocked: premium, unowned
  [*] --> EarlyLocked: early-access, before publicAt, unowned
  PremiumLocked --> Owned: ownership grant (commerce)
  EarlyLocked --> Owned: ownership grant (commerce)
  EarlyLocked --> Free: now >= publicAt
  Owned --> [*]: full audio
  Free --> [*]: full audio
  PremiumLocked --> [*]: preview only (30s)
  EarlyLocked --> [*]: preview only (30s)
```

## 9. Cross-cutting hooks

- **Auth/scope.** Reads (`/podcasts`, `/podcasts/:id`, `/podcasts/:id/episodes`) are **public**;
  ownership decoration on episodes requires a valid bearer (absent → `isOwned=false`, premium/
  early-access locked). `POST /tip` requires `fan` role; ownership of the payment method is re-checked
  in `payments`.
- **Premium preview gating (INV-3, OQ-6).** `previewSec` default **30** from `PlatformSettings`,
  configurable per content-type. Preview never serves full audio; enforced server-side in `media`.
- **Early-access gating.** Locked until `publicAt` unless owned; `Clock.now()` (UTC) is the comparison
  time, never client time.
- **Tip idempotency.** `Idempotency-Key` (header or body field) is forwarded to `IssueTip`; same key →
  same `TipResult`, no repeated charge or duplicate `TipReceived`.
- **Events.** `TipReceived { tipId, podcastId, creatorId, amount, fanId, at }` published AFTER_SUCCESS
  via outbox; consumed by `notifications` for the creator `tip` notification. No JPA entities in events.
- **Audit (INV-10).** The tip is a privileged money mutation; the audit entry is appended by
  `payments` on the `IssueTip` path (this module emits the trigger, not the ledger row).
- **Feature flag.** `flags.tipping` and `flags.podcasts` (PlatformSettings) gate the tip and browse
  surfaces respectively; flag off → `403` (`feature-off`).
- **Rate limits.** `POST /tip` is rate-limited per account (`429` + `Retry-After`).
- **Season pass (referenced, not owned here).** Purchasing a `season-pass` grants all premium episodes
  of the show — handled in `commerce` settlement expansion (**INV-2**); this module merely reflects the
  resulting grants via `OwnershipReader` (`isOwned=true`).
- **Error codes.** `NOT_FOUND`, `VALIDATION` (amount ≤ 0), `TIPS_DISABLED`, `UNAUTHENTICATED`,
  `PAYMENT_FAILED`, `IDEMPOTENCY_CONFLICT`, `RATE_LIMITED`. Uniform envelope per conventions §4.
- **Observability.** Metrics: `podcasts.episodes.listed`, `podcasts.tips.count`, `podcasts.tips.amount`,
  tip latency; spans across the `TipShow → IssueTip` call. Structured JSON logs with trace id; no PII.

## 10. Work units & build order

| WU | Scope | LLFR | Ports / tables | Depends on |
|---|---|---|---|---|
| **WU-POD-1** | Podcast shows/episodes browse + premium/early-access gating + per-caller ownership decoration | PODCAST-01.1–01.3 | `ListPodcasts`, `GetPodcast`, `ListEpisodes`; `PodcastRepository`, `OwnershipReader`, `MediaService`; tables `podcast`, `podcast_episode` | WU-CAT-1, WU-MED-1 |
| **WU-POD-2** | Tipping: `TipShow → IssueTip` (90/10), `TipReceived` event | PODCAST-02.1 | `TipShow`; `IssueTipUseCase`, `EventPublisher`; ledger via WU-PAY-3 | WU-PAY-3, WU-POD-1 |

**Recommended order:** WU-POD-1 then WU-POD-2. Cross-reference PRD §8 (Phase 4): WU-POD-1 → WU-POD-2.

## 11. Testing plan

**Unit (domain/use-case with fakes).** `EpisodeAccess` truth table (free / premium-owned /
premium-unowned / early-access before/after `publicAt`, owned/unowned). `TipShow` amount guard;
idempotency-key passthrough; `TIPS_DISABLED` when `supports_tips=false`. Fakes for `OwnershipReader`,
`MediaService`, `IssueTipUseCase`, `EventPublisher`, `Clock`.

**Integration (Testcontainers Postgres + MinIO, REST-assured).** Seed shows/episodes; assert paged
list, category filter, 404 on unknown show; episodes endpoint with and without bearer; tip flow
against a fake `payments` boundary verifying a `TipReceived` is published.

**Contract.** Responses validate against `Frontend/src/types/index.ts` (`Podcast`, `PodcastEpisode`)
and `API-CONTRACT.md` §8 (OpenAPI contract test green).

**Key Given/When/Then (PRD §6.8).**
- *Premium unowned:* **Given** a premium episode the caller does not own, **When** listing episodes,
  **Then** `isOwned=false` and full audio is withheld (media resolves to a 30s preview URL only).
- *Tip nets 90%:* **Given** a ₵10 tip that settles, **When** processed, **Then** the creator nets
  **₵9.00** (900 pesewas, platform 100), the response is `202`, and a `tip` notification is delivered
  to the creator (asserted via `TipReceived`).
- *Early-access:* **Given** an early-access episode before `publicAt` the caller does not own, **Then**
  it is locked (preview only); **Given** `now >= publicAt`, **Then** it is free to everyone.
- *Idempotent tip:* **Given** a repeated `Idempotency-Key`, **Then** no second charge and the same
  `TipResult` is returned.

**Coverage** ≥ the gate in `sdlc/testing-strategy.md`.

## 12. Definition of done (module-specific)

Global DoD (PRD §8 / conventions §11) — unit + integration tests, contract conformance, forward-only
Flyway applying cleanly, healthy under Docker Compose, ArchUnit dependency rule green, idempotent
money paths + audit (INV-10), coverage gate, Spotless clean, ADD updated — **plus**:

1. **Preview never serves full audio** for premium-unowned or pre-`publicAt` early-access episodes
   (INV-3 verified end-to-end).
2. **Per-caller ownership** is correct: authenticated owner → `isOwned=true` and full audio; anonymous
   → `isOwned=false` and locked.
3. **Tip split is exact**: ₵10 → creator 900 pesewas + platform 100, posted balanced in `payments`
   (Σ debits = Σ credits, INV-6) with no remainder leak.
4. **Tip is idempotent** (key replay → single effect) and emits exactly one `TipReceived`.
5. **No cross-module FKs**; ownership resolved only via `OwnershipReader`; season-pass grants surfaced
   from `commerce` (INV-2), not duplicated here.
