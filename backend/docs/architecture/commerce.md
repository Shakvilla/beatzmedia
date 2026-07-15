# Architecture Design Doc — `commerce` (Commerce / Orders & Ownership)

> **Status:** Stable · **PRD source:** `BACKEND-PRD.md` §6.5 · **Owning context:** `commerce` ·
> **Package root:** `org.shakvilla.beatzmedia.commerce`
>
> This ADD is consumed by Claude Code agents. It is the design contract for the module: an agent
> reads it, plans the listed work units, implements within the stated ports/adapters, writes the
> tests, and opens a PR. Do not invent endpoints or fields not traceable to the PRD / `API-CONTRACT.md`.

## 1. Purpose & responsibilities

The `commerce` module owns the buy-side of the platform: the per-user **cart** (mixed item kinds,
stackability, server-computed totals), **checkout orchestration** (validate cart, snapshot prices into
an order, then delegate the actual charge to `payments` via the `InitiateCharge` input port), **order
history**, and the authoritative **creation and revocation of `OwnershipGrant`s**. It explicitly does
**not** own money movement, payment intents, the ledger, refunds, or provider integration — those live
in `payments` (§6.6) and are reached only through input ports / domain events. It also does **not**
own catalog truth (album→track and show→episode expansion data is read from `catalog`/`podcasts` via
ports) nor ticket inventory truth (`events` module), though it triggers ticket issuance on settlement.
Surface: **Fan** only (cart, buy buttons, checkout, receipt, order history; the buy-to-own unlock that
playback later reads). HLFRs covered: **HLFR-COMMERCE-01** (cart) and **HLFR-COMMERCE-02** (checkout &
ownership grant), i.e. LLFR-COMMERCE-01.1–01.3 and 02.1–02.5.

## 2. Context & dependencies (C4 component view)

```mermaid
flowchart LR
  Fan([Fan / SPA])
  subgraph commerce[commerce module]
    IN[REST adapter\nCartResource / CheckoutResource / OrderResource]
    EVT[Event handlers\nPaymentSettled / PaymentFailed]
    APP[Application use cases\nGetCart Add/Update/Remove Checkout\nListOrders GrantOwnership RevokeOwnership]
    DOM[Domain\nCart · Order · OwnershipGrant]
    OUT[Outbound adapters\nrepos + pricing + clock + publisher]
  end
  Fan -->|/v1/me/cart* /v1/checkout /v1/me/orders| IN --> APP --> DOM
  APP --> OUT
  OUT --> DB[(Postgres: cart, cart_item,\norder, order_line, ownership_grant)]
  APP -->|InitiateCharge orderRef,amount,method,idemKey| PAY[[payments module\nInitiateChargeUseCase]]
  PAY -. PaymentSettled / PaymentFailed .-> EVT --> APP
  APP -->|read album→tracks, show→episodes| CAT[[catalog / podcasts ports]]
  APP -->|issue Ticket, decrement tier on settle| EVTS[[events module]]
  APP -->|post 70/30 creator credit on settle| LEDGER[[payments ledger port]]
  APP -->|publish OwnershipGranted, OwnershipRevoked| BUS{{Domain event bus}}
```

**Dependency rule.** `adapter.in.rest → application → domain` inward only; `domain` depends on nothing
framework-specific. Cross-module calls go **only** through input ports (`InitiateChargeUseCase`,
catalog/podcasts readers, events issuer, ledger poster) or via domain events; **persistence is never
shared** — commerce holds no FK to another module's tables (album track ids, episode ids, payment
intent id are stored as opaque ids and resolved through ports). Commerce **calls** `payments`
(`InitiateCharge`) and **consumes** `PaymentSettled`/`PaymentFailed`; it **publishes**
`OwnershipGranted` and `OwnershipRevoked`.

## 3. Domain model

| Name | Kind | Key fields | Notes |
|---|---|---|---|
| `Cart` | Aggregate root | `id`, `accountId` (unique), `items[]` | One per account; lazily created on first add. |
| `CartItem` | Entity (within Cart) | `lineId`, `kind`, `refId`, `title`, `subtitle?`, `image`, `unitPrice (Money)`, `qty`, `stackable`, `metadata` | `lineId` stable, e.g. `track:last-last`, `ticket:iron-boy-live:VIP`. |
| `Order` | Aggregate root | `id`, `accountId`, `reference (BZ-YYYY-NNNNN)`, `status`, `subtotal/fee/total (Money)`, `paymentIntentId?`, `failureReason?`, `lines[]`, `createdAt` | Immutable price snapshot taken at checkout. |
| `OrderLine` | Entity (within Order) | `id`, `kind`, `refId`, `title`, `unitPrice (Money)`, `qty` | Snapshot — never re-priced after checkout. |
| `OwnershipGrant` | Aggregate root | `id`, `accountId`, `trackId?`, `episodeId?`, `sourceOrderId`, `grantedAt`, `revokedAt?` | Exactly one of `trackId`/`episodeId` set. Active when `revokedAt IS NULL`. |
| `Money` | Value object (kernel) | `minor`, `currency` | From `platform` kernel; minor units. |
| `IdempotencyKey` | Value object (kernel) | `value` | Required on `/checkout`. |

**Enums** (lifted verbatim from frontend `CartItemKind` and PRD §6.5):

- `CartItemKind = track | album | album-rest | store | episode | season-pass | ticket`
- `OrderStatus = pending | paid | fulfilled | refunded | failed`

**Stackability rule (domain):** `track, album, album-rest, episode, season-pass` are **non-stackable**
digital one-offs (qty fixed at 1; re-add is a no-op); `ticket` and `store` (merch) are **stackable**
with qty clamped to `1..99`.

**Invariants enforced here:**

- **INV-1** — `GrantOwnership` is only ever invoked from the `PaymentSettled` handler; no code path
  creates a grant on `pending`/`failed`. Guard: `order.status == paid` before any grant.
- **INV-2** — On settle, an `album`/`album-rest` line expands to one grant per constituent **track id**;
  a `season-pass` line expands to one grant per premium **episode id** of the show.
- **INV-9** — A completed refund (driven from `payments`) invokes `RevokeOwnership`, setting
  `revoked_at` on every grant whose `source_order_id` matches.
- **INV-11** — All money is `*_minor` internally; totals (`subtotal = Σ unitPrice×qty`,
  `fee = serviceFee when items>0 else 0`, `total = subtotal+fee`) are computed half-up on minor units.

```mermaid
erDiagram
  CART ||--o{ CART_ITEM : contains
  ORDER ||--o{ ORDER_LINE : contains
  ORDER ||--o{ OWNERSHIP_GRANT : "source_order_id"
  CART {
    uuid id PK
    string account_id "UNIQUE, opaque ref"
  }
  CART_ITEM {
    uuid id PK
    uuid cart_id FK
    string kind
    string ref_id
    string title
    string subtitle
    string image
    bigint unit_price_minor
    int qty
    bool stackable
    jsonb metadata
  }
  ORDER {
    uuid id PK
    string account_id
    string reference "UNIQUE BZ-YYYY-NNNNN"
    string status
    bigint subtotal_minor
    bigint fee_minor
    bigint total_minor
    string payment_intent_id
    string failure_reason
    timestamptz created_at
  }
  ORDER_LINE {
    uuid id PK
    uuid order_id FK
    string kind
    string ref_id
    string title
    bigint unit_price_minor
    int qty
  }
  OWNERSHIP_GRANT {
    uuid id PK
    string account_id
    string track_id
    string episode_id
    uuid source_order_id FK
    timestamptz granted_at
    timestamptz revoked_at
  }
```

## 4. Application layer (ports)

### 4.1 Input ports (use cases)

```java
public interface GetCartUseCase {
    CartView getCart(AccountId account);
}

public interface AddCartItemUseCase {
    CartView add(AccountId account, AddCartItemCommand command);
}

public interface UpdateCartItemUseCase {
    CartView updateQuantity(AccountId account, CartLineId lineId, int qty);
}

public interface RemoveCartItemUseCase {
    CartView remove(AccountId account, CartLineId lineId);
}

public interface CheckoutUseCase {
    CheckoutResult checkout(AccountId account, IdempotencyKey key, PaymentMethodId method);
}

public interface ListOrdersUseCase {
    Page<OrderSnapshot> listOrders(AccountId account, PageRequest page);
}

/** Internal — invoked only by the PaymentSettled handler (INV-1). */
public interface GrantOwnershipUseCase {
    void grantForSettledOrder(OrderId orderId);
}

/** Internal — invoked only by the refund/PaymentRefunded handler (INV-9). */
public interface RevokeOwnershipUseCase {
    void revokeForOrder(OrderId orderId, RefundReason reason);
}

// Commands / results (records)
public record AddCartItemCommand(CartItemKind kind, String refId, Integer qty,
                                 Map<String, Object> metadata) {}
public record CartView(List<CartItemDto> items, Money subtotal, Money fee, Money total, int count) {}
public sealed interface CheckoutResult permits CheckoutResult.Pending, CheckoutResult.Settled {
    record Pending(OrderId orderId, PaymentIntentId paymentIntentId) implements CheckoutResult {}
    record Settled(OrderSnapshot snapshot) implements CheckoutResult {}
}
```

| Port | Trigger | Authorization | Idempotency | Emitted events | LLFR |
|---|---|---|---|---|---|
| `GetCartUseCase` | `GET /me/cart` | fan; own cart only | n/a (read) | — | 01.1 |
| `AddCartItemUseCase` | `POST /me/cart/items` | fan; own cart | non-stackable re-add = no-op | — | 01.2 |
| `UpdateCartItemUseCase` | `PATCH /me/cart/items/:lineId` | fan; own cart | idempotent (qty set, not delta) | — | 01.3 |
| `RemoveCartItemUseCase` | `DELETE /me/cart/items/:lineId` | fan; own cart | idempotent (remove-missing ok) | — | 01.3 |
| `CheckoutUseCase` | `POST /checkout` | fan; own cart | **`Idempotency-Key`** → same order/intent | (none until settle) | 02.1 |
| `ListOrdersUseCase` | `GET /me/orders` | fan; own orders | n/a (read) | — | 02.4 |
| `GrantOwnershipUseCase` | `PaymentSettled` event | system (internal) | idempotent on `orderId` | `OwnershipGranted`, `sale` notif | 02.2 / 02.5 |
| `RevokeOwnershipUseCase` | refund completed event | system (internal) | idempotent on `orderId` | `OwnershipRevoked` | INV-9 |

### 4.2 Output ports

```java
public interface CartRepository {
    Optional<Cart> findByAccount(AccountId account);
    Cart save(Cart cart);
    void delete(CartId id);
}

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(OrderId id);
    Optional<Order> findByReference(String reference);
    Page<Order> findByAccount(AccountId account, PageRequest page);
}

public interface OwnershipRepository {
    OwnershipGrant save(OwnershipGrant grant);
    boolean existsActiveForTrack(AccountId account, TrackId track);
    boolean existsActiveForEpisode(AccountId account, EpisodeId episode);
    List<OwnershipGrant> findBySourceOrder(OrderId orderId);
    // Added WU-POD-1: batched read backing the new GetOwnedEpisodeIds input port below.
    List<String> activeEpisodeIds(AccountId account, List<String> candidateEpisodeIds);
}
```

**`GetOwnedEpisodeIds` input port (added WU-POD-1).** Before this WU, no commerce input port
exposed per-episode ownership to other modules — only `library.GetOwnedTrackIds` existed (track
ownership only). The `podcasts` module needed a caller-owns-this-episode check for its INV-3
episode gating (`ListEpisodes` decoration + `GetEpisodeStreamUrl`), so commerce added:

```java
public interface GetOwnedEpisodeIds {
    boolean isOwned(AccountId account, String episodeId);
    Set<String> ownedOf(AccountId account, List<String> candidateEpisodeIds); // batched, avoids N+1
}
```

Consumed in-process by the `podcasts` module's `OwnershipReader` output-port adapter — podcasts
never reads `ownership_grant` directly. Symmetric to how playback consumes library's
`GetOwnedTrackIds`.

```java
/** Server-side price/expansion source; never trusts the client (INV-2, INV-11). */
public interface PricingService {
    PricedItem priceFor(CartItemKind kind, String refId, Map<String, Object> metadata);
    List<TrackId> tracksOfAlbum(String albumRefId);
    List<EpisodeId> premiumEpisodesOfShow(String seasonPassRefId);
}

public interface EventPublisher {
    void publish(DomainEvent event); // AFTER_SUCCESS
}

public interface Clock { Instant now(); }

/** Cross-module input port owned by payments (§6.6) — commerce calls it. */
public interface InitiateChargeUseCase {
    PaymentIntent initiateCharge(String orderReference, Money amount,
                                 PaymentMethodId method, IdempotencyKey key);
}
```

Implementing adapters (one line each): `CartRepository`/`OrderRepository`/`OwnershipRepository` →
Panache/JDBC persistence adapters in `adapter.out.persistence` (domain↔JPA mapping, no ORM on domain);
`PricingService` → adapter calling `catalog`/`podcasts`/`store` input ports + `PlatformSettings`;
`EventPublisher` → CDI/transactional-outbox publisher; `Clock` → `platform` kernel clock;
`InitiateChargeUseCase` → injected directly (in-process call to the `payments` application service).

## 5. Adapters

### 5.1 Inbound — REST resources

| Method | Path | Auth/scope | Request DTO | Response DTO | Success | Error codes | LLFR |
|---|---|---|---|---|---|---|---|
| GET | `/v1/me/cart` | fan | — | `CartView` | 200 | 401 | 01.1 |
| POST | `/v1/me/cart/items` | fan | `AddCartItemRequest {id?,kind,refId,qty?,metadata?}` | `CartView` | 200 | 401, 409 `ALREADY_OWNED`, 409 `TIER_SOLD_OUT`, 422 | 01.2 |
| PATCH | `/v1/me/cart/items/:lineId` | fan | `UpdateCartItemRequest {qty}` | `CartView` | 200 | 401, 404, 409 `NOT_STACKABLE`, 422 | 01.3 |
| DELETE | `/v1/me/cart/items/:lineId` | fan | — | `CartView` | 200 | 401, 404 | 01.3 |
| POST | `/v1/checkout` | fan + `Idempotency-Key` | `CheckoutRequest {paymentMethodId, idempotencyKey}` | `CheckoutResponse {orderId, paymentIntentId, status}` **or** `OrderSnapshot` | **202** (async MoMo) / **200** (sync-settled rail) | 401, 409 `CART_EMPTY`, 409 `ALREADY_OWNED`, 409 `TIER_SOLD_OUT`, 422 | 02.1 |
| GET | `/v1/me/orders` | fan | `?page=&size=` | `Page<OrderSnapshot>` | 200 | 401 | 02.4 |

Resources are thin: map DTO → command, call input port, map result → DTO, map domain exception →
error envelope via the per-family `ExceptionMapper`. No business logic in resources.

### 5.2 Outbound — persistence & integrations

- **Persistence adapter** maps each domain aggregate ↔ JPA entity (`CartEntity`/`CartItemEntity`,
  `OrderEntity`/`OrderLineEntity`, `OwnershipGrantEntity`); money columns are `BIGINT *_minor`, cedis
  conversion happens only at the REST boundary. Transaction boundary = the use case
  (`@Transactional` on the application service impl).
- **`payments` integration:** `InitiateChargeUseCase` called inside the checkout transaction; the
  returned `PaymentIntent.id` is persisted on the order. Settlement arrives **asynchronously** via the
  `PaymentSettled`/`PaymentFailed` domain-event handlers (separate transactions).
- **Catalog/podcasts/store readers** resolve album→tracks, show→episodes, and unit prices for
  `PricingService`. **Events module** is invoked on a settled `ticket` line to issue the `Ticket` and
  decrement tier availability. **Ledger poster** (payments port) records the 70/30 creator credit on
  settle (INV-4).
- **Event publishing** uses a transactional outbox so `OwnershipGranted`/`OwnershipRevoked` publish
  `AFTER_SUCCESS` (kernel §8); events carry ids + a minimal snapshot, never JPA entities.

## 6. DTOs & API shapes

Money on the wire is `{ amount: <decimal cedis>, currency: "GHS" }`; timestamps ISO-8601; durations
whole seconds. Field lists traceable to frontend `cart-context.tsx` / `types/index.ts`.

**`CartItem`** (response, mirrors frontend `CartItem`):

| Field | Type | Notes |
|---|---|---|
| `id` | string | line id, e.g. `track:last-last` |
| `kind` | `CartItemKind` | enum |
| `title` | string | |
| `subtitle` | string? | optional |
| `image` | string | cover/thumbnail URL |
| `price` | `Money` | unit price `{amount,currency}` |
| `quantity` | int | 1 for non-stackable; 1–99 for stackable |
| `stackable` | bool? | true for `ticket`/`store` |

**`OrderSnapshot`** (response, mirrors frontend `OrderSnapshot`):

| Field | Type | Notes |
|---|---|---|
| `items` | `OrderLine[]` | snapshot lines |
| `subtotal` | `Money` | Σ unit×qty |
| `fee` | `Money` | service fee (₵0.50) when non-empty |
| `total` | `Money` | subtotal + fee |
| `reference` | string | `BZ-YYYY-NNNNN` |
| `orderId` | string | |
| `status` | `OrderStatus` | |
| `createdAt` | string | ISO-8601 |
| `tickets` | `Ticket[]?` | present when ticket lines settled (02.5) |

**`OrderLine`** (response):

| Field | Type | Notes |
|---|---|---|
| `id` | string | |
| `kind` | `CartItemKind` | |
| `refId` | string | catalog/episode/event ref |
| `title` | string | snapshotted |
| `unitPrice` | `Money` | snapshotted at checkout |
| `quantity` | int | |

## 7. Persistence schema & migrations

```sql
-- V20__commerce_cart.sql
CREATE TABLE cart (
    id          UUID PRIMARY KEY,
    account_id  VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE cart_item (
    id               UUID PRIMARY KEY,
    cart_id          UUID NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    ref_id           VARCHAR(64) NOT NULL,
    title            VARCHAR(256) NOT NULL,
    subtitle         VARCHAR(256),
    image            VARCHAR(512),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0),
    qty              INT NOT NULL DEFAULT 1 CHECK (qty BETWEEN 1 AND 99),
    stackable        BOOLEAN NOT NULL DEFAULT FALSE,
    metadata         JSONB,
    UNIQUE (cart_id, kind, ref_id)            -- collapse duplicate non-stackable adds
);
CREATE INDEX ix_cart_item_cart ON cart_item (cart_id);

-- V21__commerce_order.sql
CREATE TABLE "order" (
    id                UUID PRIMARY KEY,
    account_id        VARCHAR(64) NOT NULL,
    reference         VARCHAR(16) NOT NULL UNIQUE,      -- BZ-YYYY-NNNNN
    status            VARCHAR(16) NOT NULL,             -- pending|paid|fulfilled|refunded|failed
    subtotal_minor    BIGINT NOT NULL,
    fee_minor         BIGINT NOT NULL,
    total_minor       BIGINT NOT NULL,
    payment_intent_id VARCHAR(64),
    failure_reason    VARCHAR(256),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_account_created ON "order" (account_id, created_at DESC);
CREATE INDEX ix_order_payment_intent ON "order" (payment_intent_id);

CREATE TABLE order_line (
    id               UUID PRIMARY KEY,
    order_id         UUID NOT NULL REFERENCES "order"(id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    ref_id           VARCHAR(64) NOT NULL,
    title            VARCHAR(256) NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    qty              INT NOT NULL CHECK (qty BETWEEN 1 AND 99)
);
CREATE INDEX ix_order_line_order ON order_line (order_id);

-- V22__commerce_ownership_grant.sql
CREATE TABLE ownership_grant (
    id              UUID PRIMARY KEY,
    account_id      VARCHAR(64) NOT NULL,
    track_id        VARCHAR(64),
    episode_id      VARCHAR(64),
    source_order_id UUID NOT NULL REFERENCES "order"(id),
    granted_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    CHECK ( (track_id IS NOT NULL) <> (episode_id IS NOT NULL) )   -- exactly one target
);
-- one active grant per (account, track) / (account, episode); revoked rows excluded (INV-1/INV-9)
CREATE UNIQUE INDEX ux_grant_account_track
    ON ownership_grant (account_id, track_id) WHERE revoked_at IS NULL AND track_id IS NOT NULL;
CREATE UNIQUE INDEX ux_grant_account_episode
    ON ownership_grant (account_id, episode_id) WHERE revoked_at IS NULL AND episode_id IS NOT NULL;
CREATE INDEX ix_grant_source_order ON ownership_grant (source_order_id);
CREATE INDEX ix_grant_account ON ownership_grant (account_id);
```

**Flyway list (illustrative version numbers — see §13 for the actual allocated version):**
`V20__commerce_cart.sql`, `V21__commerce_order.sql`, `V22__commerce_ownership_grant.sql`.
Forward-only; dev seed contributes sample cart/orders to `R__seed_dev_data.sql` (dev/test only).
**WU-COM-1 shipped `cart`/`cart_item` as `V943__commerce_cart.sql`** — always allocate the real next
version with `backend/scripts/next-migration-version.sh` at PR time rather than assuming the
`V2x` numbers shown above.

## 8. Key flows

```mermaid
sequenceDiagram
  actor Fan
  participant REST as CheckoutResource
  participant CO as CheckoutUseCase
  participant OR as OrderRepository
  participant PAY as InitiateChargeUseCase (payments)
  participant H as PaymentSettled handler
  participant OW as OwnershipRepository
  participant CAT as catalog/podcasts
  participant LED as ledger (payments)
  participant CART as CartRepository
  participant BUS as EventPublisher

  Fan->>REST: POST /v1/checkout {paymentMethodId} + Idempotency-Key
  REST->>CO: checkout(account,key,method)
  CO->>CART: load cart (409 CART_EMPTY if empty)
  CO->>CO: recompute subtotal/fee/total server-side (INV-11)
  CO->>OR: save Order(status=pending) + order_lines (snapshot)
  CO->>PAY: initiateCharge(reference,total,method,key)
  PAY-->>CO: PaymentIntent(pending)
  CO-->>REST: Pending(orderId,intentId)
  REST-->>Fan: 202 {orderId,paymentIntentId,status:pending}
  Note over PAY,H: async — provider webhook later
  PAY-->>H: PaymentSettled(orderId)
  H->>OR: load order; guard pending->paid (idempotent on orderId)
  H->>OR: order.status = paid
  H->>CAT: expand album->tracks, season-pass->episodes (INV-2)
  H->>OW: create OwnershipGrant per track/episode (INV-1)
  H->>LED: post 70/30 creator credit (INV-4)
  H->>CART: clear cart
  H->>BUS: publish OwnershipGranted + sale notifications
  Note over H: ticket lines -> issue Ticket, decrement tier (02.5)
  H-->>Fan: receipt OrderSnapshot (reference BZ-YYYY-NNNNN)
```

```mermaid
stateDiagram-v2
  [*] --> pending: checkout (InitiateCharge)
  pending --> paid: PaymentSettled (grant ownership)
  pending --> failed: PaymentFailed (cart preserved, no grant)
  paid --> fulfilled: tickets issued / digital delivered
  paid --> refunded: refund completed (RevokeOwnership, INV-9)
  fulfilled --> refunded: refund completed (RevokeOwnership, INV-9)
  failed --> [*]
  refunded --> [*]
```

## 9. Cross-cutting hooks

- **Auth/scope.** All endpoints require `fan`; the application layer re-checks resource ownership
  (`cart.accountId == jwt.sub`, orders queried by `account_id` only). Private orders not owned by the
  caller → 404 (existence hidden).
- **Idempotency on `/checkout` (§9.2).** `Idempotency-Key` header is mandatory; the same key returns
  the **same** order + payment intent with no second charge. Commerce forwards the key to
  `InitiateCharge` (which is itself idempotent on the key) and persists `payment_intent_id` so a
  replay short-circuits to the existing order.
- **Idempotent event handlers (INV-1).** `PaymentSettled`/`PaymentFailed`/refund handlers are keyed on
  `orderId`: re-processing a settled order is a no-op (status guard + unique grant indexes prevent
  duplicate grants). Ownership is created **only** inside the `PaymentSettled` handler — never on
  `pending`/`failed`.
- **Refund revokes ownership (INV-9).** A completed refund from `payments` drives `RevokeOwnership`,
  setting `revoked_at` on all grants with that `source_order_id` and moving the order to `refunded`;
  the unique-active indexes then permit a future re-purchase.
- **Stackability.** Non-stackable kinds reject qty changes (`NOT_STACKABLE`) and collapse re-adds to a
  no-op; stackable kinds clamp `1..99`.
- **Audit (INV-10).** Refund-driven revocation appends an `AuditEntry` via the platform interceptor.
- **Error codes:** `CART_EMPTY` (409), `ALREADY_OWNED` (409), `NOT_STACKABLE` (409),
  `TIER_SOLD_OUT` (409), plus `ILLEGAL_TRANSITION` (409) for bad order-status moves; validation → 422.
- **Rate limiting.** `/checkout` is rate-limited per account/IP (429 + `Retry-After`, §9.5).
- **Observability.** Metrics: cart size, checkout count, checkout→settle latency, settlement success
  rate, grant count, revoke count. Spans cover REST → use case → `InitiateCharge`/DB; trace id on
  every request and propagated into the settlement handler via the event.

## 10. Work units & build order

| WU | Scope | LLFR coverage | Tables | Depends on | Order |
|---|---|---|---|---|---|
| **WU-COM-1** | Cart with stackability + server-computed totals | COMMERCE-01.1–01.3 | `cart`, `cart_item` | WU-PLT-1, WU-CAT-1 | 1 |
| **WU-COM-2** | Checkout orchestration + settlement→ownership grant + orders + ticket issuance | COMMERCE-02.1–02.5 | `order`, `order_line`, `ownership_grant` (+ `ticket` via events) | WU-COM-1, **WU-PAY-1**, **WU-PAY-3** | 2 |

Cross-ref PRD §8 Phase 2: `WU-PAY-1 → WU-PAY-3`; `WU-COM-1 → WU-COM-2` (needs PAY-1/3). Downstream:
`WU-PLY-1` (ownership-aware streaming) and `WU-PAY-5` (refund→revoke) depend on WU-COM-2.

## 11. Testing plan

- **Unit (domain + use cases with fakes):** stackability rules; totals math on minor units
  (`fee=₵0.50` when non-empty, half-up rounding); order-status state machine guards;
  album/season-pass expansion fan-out; grant uniqueness.
- **Integration (Testcontainers Postgres, REST-assured):** cart CRUD; checkout returns 202 with order
  `pending` and a persisted `payment_intent_id`; settlement handler grants + clears cart; refund
  revokes; unique-active grant index enforces single active grant.
- **Contract:** `CartView`, `OrderSnapshot`, `OrderLine` validate against frontend types / API contract.

PRD §6.5 Given/When/Then acceptance cases:

- **Album expansion (02.2):** *Given* a settled album purchase *When* `PaymentSettled` fires *Then*
  every track of the album appears in `/me/owned`, the order is `paid`, and creator balance reflects
  70% of the album price.
- **Already in cart (01.2):** *Given* a track already in cart *When* added again *Then* cart unchanged
  (qty stays 1); *Given* a ticket added twice *Then* qty=2.
- **Failed charge preserves cart (02.3):** *Given* a failed charge *When* `PaymentFailed` fires *Then*
  no ownership grant exists and the cart is preserved for retry; `GET` order shows the failure reason.
- **Sold-out tier (02.5):** *Given* the last ticket of a tier settles *Then* the tier reports
  `soldOut=true` and a further add → 409 `TIER_SOLD_OUT`.
- **Idempotent checkout (§9.2):** *Given* the same `Idempotency-Key` twice *Then* one order, one
  payment intent, no double charge.

Coverage ≥ the gate in `sdlc/testing-strategy.md`.

## 12. Definition of done (module-specific)

Global DoD (conventions §11 / PRD §8) **plus**:

1. **No ownership without settlement (INV-1):** an `OwnershipGrant` is created *only* in the
   `PaymentSettled` handler; ArchUnit/usage test proves `GrantOwnershipUseCase` has no other caller and
   pending/failed orders never produce grants.
2. **Totals computed server-side:** checkout recomputes subtotal/fee/total from `PricingService`,
   never trusting client-supplied amounts; verified by a test that sends tampered prices.
3. **Idempotent settlement:** replaying `PaymentSettled` for the same `orderId` produces no duplicate
   grants, no duplicate ledger credits, and a single cart clear (enforced by status guard + unique
   active grant indexes).
4. **Refund revokes ownership (INV-9):** a refund sets `revoked_at` on all matching grants and moves
   the order to `refunded`, restoring re-purchase eligibility.
5. **Album/season expansion (INV-2):** album/season-pass lines fan out to all constituent
   track/episode grants on settle.

## 13. WU-COM-1 implementation notes (cart shipped)

WU-COM-1 shipped the cart slice only (`GetCart`, `AddCartItem`, `UpdateCartItem`, `RemoveCartItem`;
LLFR-COMMERCE-01.1–01.3). The following notes record decisions made while implementing to this ADD
and should guide WU-COM-2 (checkout/order/ownership-grant), which reuses the same `cart`/`cart_item`
tables and clears them on settlement.

- **Migration filename/version.** §7's `V20__commerce_cart.sql` example predates the repo's global
  Flyway sequencing convention (`backend/scripts/next-migration-version.sh` allocates the next free
  version across *all* modules, not a per-module band). The cart/cart_item tables actually shipped as
  **`V943__commerce_cart.sql`**. WU-COM-2 must allocate its own next-free version for `order`,
  `order_line`, `ownership_grant` at PR time rather than assuming `V21`/`V22`.
- **Id columns are `TEXT`, not native Postgres `UUID`.** `cart.id` and `cart_item.id` are `TEXT`
  primary keys populated by the platform `IdGenerator` (UUIDv7 strings), matching the
  codebase-wide convention for entity ids (e.g. `track.id`, `album.id`) rather than the native
  `UUID` column type used nowhere else in the schema. Using `UUID` caused a Postgres
  `operator does not exist: uuid = character varying` error on `EntityManager#find` lookups by
  string id — a real bug caught by `CommerceIT` against Testcontainers Postgres.
- **`CartLineId` is the natural key, not a separate identity.** `cart_item.line_id` (`kind:refId`,
  e.g. `track:last-last`) is UNIQUE per `cart_id` (`uq_cart_item_cart_line`) and is what
  PATCH/DELETE `:lineId` path segments address; `cart_item.id` is only the JPA/db surrogate key.
  This is what makes non-stackable re-add collapse to a no-op (`Cart.addItem` looks up by
  `CartItem.lineIdFor(kind, refId)` before deciding to no-op vs. increment vs. insert).
- **`PricingService` (output port, `CatalogPricingServiceAdapter`).** `track`/`album`/`album-rest`
  resolve authoritatively from catalog's own JPA entities (`TrackEntity`/`AlbumEntity`) via an
  in-process adapter reading the same schema — mirroring the established pattern in
  `library.adapter.out.persistence.CatalogReaderAdapter` (no cross-module FK, no cross-module input
  port needed for a same-process read-only lookup). `episode`/`season-pass`/`ticket`/`store` have
  **no backing module yet** — `podcasts` (WU-POD-1), `events` (WU-EVT-1), and `store` (WU-STO-1) are
  Phase 4 work units that ship long after this Phase-2 WU. For those four kinds the adapter requires
  and validates caller-supplied `metadata.title` (string) and `metadata.priceMinor` (positive
  integer), rejecting missing/invalid values with 404/422 rather than inventing a price. **When
  WU-POD-1/WU-EVT-1/WU-STO-1 ship, replace this interim metadata-echo branch with real price-lookup
  input ports** (`podcasts`/`events`/`store` `application.port.in`), per the hexagonal cross-module
  rule — do not keep trusting client-supplied prices for those kinds once the owning module exists.
- **`OwnershipReader` (output port, `LibraryOwnershipReaderAdapter`).** Delegates to library's
  existing `GetOwnedTrackIds` input port (WU-LIB-1) for the `ALREADY_OWNED` (409) check — but
  `GetOwnedTrackIds` only enumerates **track** ids today, so only `kind=track` adds can be rejected
  as already-owned in this WU; album/episode/season-pass/ticket/store ownership checks are no-ops
  until WU-COM-2 introduces `ownership_grant` and library's `LibraryOwnershipReader` output port is
  repointed from `StubLibraryOwnershipReaderAdapter` to a real commerce-backed adapter (as already
  flagged in that stub's own Javadoc). Because `StubLibraryOwnershipReaderAdapter` always returns
  `List.of()` in this environment, the `ALREADY_OWNED` HTTP path (409) is exercised only at the
  application-service unit-test layer (`AddCartItemServiceTest`, `FakeOwnershipReader`) — end-to-end
  REST coverage of a real 409 lands with WU-COM-2's ownership_grant data.
- **PATCH clamps rather than rejects out-of-range `qty`.** Per §4.1's port table and this section's
  "clamp `1..99`" wording, `UpdateCartItemService`/`Cart.updateQuantity` clamp silently (matching
  `AddCartItem`'s clamp behavior) rather than returning a validation error for `qty<1` or `qty>99`;
  only a non-stackable target line returns `NOT_STACKABLE` (409).
- **Shared `ErrorCode` catalog.** `ALREADY_OWNED` and `NOT_STACKABLE` were added to the platform
  `ErrorCode` enum and mapped to HTTP 409 in the shared `DomainExceptionMapper`
  (`platform.adapter.in.rest`), following the same pattern as identity/catalog/payments codes —
  commerce does **not** have its own local exception mapper.

## 14. WU-COM-2 implementation notes (checkout + settlement→ownership shipped)

WU-COM-2 shipped the checkout/order/ownership-grant slice (LLFR-COMMERCE-02.1–02.4; 02.5 ticket
issuance is deferred, see G3 below). As-built decisions on top of this ADD:

- **Migration `V944__commerce_order_and_ownership.sql`.** Allocated via
  `scripts/next-migration-version.sh` (next free global slot after `V943`). Creates `"order"`
  (quoted — reserved word), `order_line`, `ownership_grant`, the `order_grant_posting` exactly-once
  claim header, and the `order_ref_seq` SEQUENCE. Ids are `TEXT` UUIDv7 (codebase convention).
- **`order_ref` collision-safety (WU-PLT-1 / WU-COM-1 carryover, PR #10) — FIXED HERE.** The unsafe
  in-memory `AtomicLong` in `Uuidv7IdGenerator.newOrderRef()` is superseded by
  `SequenceOrderRefGenerator` (output port `OrderRefGenerator`), which draws `NNNNN` from the DB
  `order_ref_seq` SEQUENCE (monotonic across JVM restarts + horizontally-scaled instances). A
  `uq_order_reference` UNIQUE constraint on `"order".reference` is the durable loud backstop. The
  `Uuidv7IdGenerator.newOrderRef()` method is left in place (still referenced by nothing on the
  checkout path) but is no longer the source of order references; it can be removed once no caller
  remains. Proven by `CheckoutFlowIT.orderReferences_areUnique_acrossCheckouts` (format + distinctness)
  and the `uq_order_reference` constraint.
- **Ownership grant is INV-1 exactly-once (reuses the WU-PAY-3 pattern).** `GrantOwnershipService`
  (the ONLY creator of an `OwnershipGrant`; no REST caller — guarded by `GrantOwnershipCallerTest`) is
  invoked solely by `PaymentSettledSubscriber`, which `@Observes(AFTER_SUCCESS) PaymentSettled` — the
  sanctioned cross-module link (commerce consumes payments' domain event; it never reads payments'
  tables). The grant runs in a `REQUIRES_NEW` transaction and takes a per-order exactly-once claim on
  `order_grant_posting` (PRIMARY KEY on `order_id`, atomic `INSERT … ON CONFLICT DO NOTHING`) BEFORE
  any grant/credit — a re-delivered settlement (webhook replay + poll race) loses the claim and the
  whole fan-out is skipped. The `ux_grant_account_track/episode` partial-unique indexes are the
  per-target durable backstop. Proven by
  `GrantOwnershipServiceTest.grant_redeliveredSettlement_grantsExactlyOnce_idempotent` (unit) and
  `CheckoutFlowIT.redeliveredSettlement_grantsOnce_creditsOnce` (integration, two webhooks / one grant
  / one credit).
- **INV-2 album expansion + INV-4 sale split.** On settlement, `CatalogExpansionReader`
  (`CatalogExpansionReaderAdapter`, in-process read over catalog's own `track`/`album` entities)
  expands an `album`/`album-rest` line to every constituent track id, and resolves the recipient
  creator (artist) for each line. The 70/30 split is posted via `SaleLedgerPoster`
  (`PaymentsSaleLedgerPosterAdapter` → payments `LedgerPostingService.postSaleSplit`, the WU-PAY-3
  reusable primitive, ADR-21); the fee % comes from `PlatformSettings.platformFeePct` inside payments,
  never hard-coded. Season-pass→episode expansion is inert (gated — see G3). Proven by
  `GrantOwnershipServiceTest.grant_albumPurchase_expandsToAllConstituentTracks_INV2` and
  `CheckoutFlowIT.checkout_albumThenSettle_expandsToAllTracks_INV2`.
- **Server-side authoritative re-pricing (G1 / DoD 2).** `CheckoutService` re-resolves every unit
  price from `PricingService` at checkout and computes totals from the re-priced lines — the
  cart-stored price is NEVER trusted. Proven by
  `CheckoutServiceTest.checkout_tamperedCartPrice_chargesTrueServerPrice_notClientAmount` /
  `checkout_inflatedCartPrice_stillChargesTrueServerPrice` (unit) and
  `CheckoutFlowIT.checkout_tamperedCartPrice_chargesTrueServerPrice` (integration — forges the
  persisted cart amount to 1 pesewa, asserts the charge is the true 550).
- **G3 kind gate (ADR-23) — SAFE default, security-reviewer sign-off REQUIRED.** Checkout GATES
  `episode`/`season-pass`/`ticket`/`store` (409 `CHECKOUT_KIND_UNSUPPORTED`) because their price comes
  from the interim client-supplied metadata echo (`CatalogPricingServiceAdapter.priceFromMetadata`,
  `// G2`) and would let a fan spoof a real-money price; only `track`/`album`/`album-rest` (priced
  authoritatively from catalog) may be purchased. Consequently **ticket issuance (LLFR-COMMERCE-02.5)
  is deferred to WU-EVT-1** and `OrderSnapshot.tickets` is always absent in this WU. Proven by
  `CheckoutServiceTest.checkout_ticketKind_isGated_G3` / `checkout_episodeAndSeasonPassAndStore_areGated_G3`
  / `checkout_albumKind_isAllowed_G3` and `CheckoutFlowIT.checkout_ticketKind_isGated_409`.
- **Money-path hardening (WU-PAY-1 carryover).** `POST /v1/checkout` requires an `Idempotency-Key`
  header (422 if missing; same key → same order+intent, no second charge — `uq_order_account_idem`).
  Per-account token-bucket rate limiting (`CheckoutRateLimiter`) → 429 + `Retry-After`. A charge
  ceiling from `PlatformSettings.maxChargeMinor()` rejects an absurd re-priced total with a bounded
  422 `CHARGE_AMOUNT_EXCEEDED` (never an unmapped 500). Ownership authz is structural: the cart/order
  are keyed by the authenticated account, so there is no cross-account checkout. Exactly one
  `AuditEntry` per checkout and per grant (INV-10).
- **Library repoint.** `commerce.adapter.out.integration.CommerceLibraryOwnershipReaderAdapter` now
  supplies library's `LibraryOwnershipReader` output port from real `ownership_grant` rows (via
  commerce's `OwnershipRepository`); the WU-LIB-1 `StubLibraryOwnershipReaderAdapter` becomes an
  inactive `@Alternative`. `/me/owned` and library collection now reflect commerce grants. Hexagonal
  boundary preserved — library sees only track-id strings, never a commerce entity.
- **New `ErrorCode`s (WU-COM-2).** `CART_EMPTY` (409), `CHECKOUT_KIND_UNSUPPORTED` (409),
  `CHARGE_AMOUNT_EXCEEDED` (422) added to the platform `ErrorCode` enum + `DomainExceptionMapper`.
- **PaymentFailed handling (LLFR-COMMERCE-02.3).** `PaymentSettledSubscriber.onFailed`
  `@Observes(AFTER_SUCCESS) PaymentFailed` marks the order `failed` with the reason and **preserves the
  cart** for retry; no grant is created (INV-1).

### 14.0 WU-PAY-5 ownership revocation on refund (INV-9, as-built)

The refund/clawback machinery lives in `payments` (WU-PAY-5); commerce owns only the **grant
revocation**, reached the same sanctioned way grants are created — by consuming a payments domain event,
never by a cross-module table read.

- **`OrderRefundedSubscriber`** `@Observes(AFTER_SUCCESS) payments.domain.OrderRefunded` (fired after the
  refund clawback commits) → **`RevokeOwnershipService.revokeForRefundedOrder`** (the ONLY revoker; no
  REST caller), implementing the ADD's `RevokeOwnership` input port.
- **Runs `REQUIRES_NEW`**, resolves the order by the reference carried on the event (`FOR UPDATE`),
  guards `paid → refunded`, and revokes EVERY active `ownership_grant` for the order — an album/season
  refund revokes ALL its constituent track/episode grants (the grants were materialised one-per-unit at
  grant time, INV-2). Each grant `revoke` is idempotent (`revoked_at` set once), and the order-row lock +
  `paid → refunded` guard make a re-delivered refund/chargeback event, or two concurrent refunds, revoke
  exactly once (no double-revoke). Appends one `AuditEntry` (INV-10).
- No payments-table read: the order reference travels on `OrderRefunded`; commerce owns `ownership_grant`.
  Proven end-to-end by `payments … RefundDisputeIT` (revoke on admin refund + on lost chargeback; album
  refund revokes all tracks; re-delivery and two-concurrent-refunds revoke exactly once).

### 14.1 Adversarial-review fixes (F1/F2/F3)

- **F1 — per-creator sale split (multi-artist orders).** The settlement→grant service originally posted
  one ledger split per order *line* using the same `paymentIntentId` as the source ref. Because payments
  keys `ledger_posting` on `(ref_type, ref_id)` (PK), a ≥2-distinct-creator order collided on the second
  creator's claim (23505 → `DuplicatePostingException`), and — since the post ran inside the grant
  transaction — poisoned it: the *entire* settlement→grant rolled back (grants, credit, `pending→paid`,
  cart clear, `order_grant_posting` claim, audit, event). Buyer charged, nothing granted, deterministic
  on re-delivery. A two-artist cart is normal, so this was CRITICAL. **Fix:** `GrantOwnershipService`
  now aggregates line grosses **per creator** (`LinkedHashMap`) and posts **one** split per creator with
  a **per-creator-unique** ref `paymentIntentId + ":" + creatorAccountId` — distinct creators never
  collide; same-creator lines sum into one balanced posting. `PaymentsSaleLedgerPosterAdapter.postSaleSplit`
  now runs `REQUIRES_NEW` (crossing the CDI proxy from `GrantOwnershipService`), so a *genuine* duplicate
  rolls back only that split's transaction and can never poison the grant transaction. Idempotency across
  re-deliveries stays guaranteed by the order-level `order_grant_posting` claim (a re-delivered settlement
  returns before any posting). Proven by `GrantOwnershipServiceTest.grant_multiCreatorOrder_creditsEachCreatorOnce_grantsAllTracks_F1`,
  `grant_multiLineSameCreator_postsOneAggregatedSplit_F1`, `grant_multiCreatorRedelivery_creditsEachOnce_F1`
  (unit; `FakeSaleLedgerPoster` now throws on a duplicate `(intent, refId)` so a regression fails), and
  `CheckoutFlowIT.checkout_twoDistinctArtists_settle_creditsBothOnce_grantsAllTracks` (integration: both
  creators credited exactly once, all tracks granted, ledger balanced `INV-6`, idempotent on re-delivery).
- **F2 — `album-rest` ownership-aware pricing.** Full `album` purchase is unchanged and correct: the
  album `list_price_minor` already bakes in the 24% bundle discount at authoring (release wizard sets it
  to `sum(track prices) × (1 − bundleDiscountPct)`), so checkout must NOT re-apply `bundleDiscountPct`
  (it stays a catalog-authoring constant; a note to that effect is on `PlatformSettings.bundleDiscountPct`).
  `album-rest` ("buy the rest"), however, must match the frontend (`album/$albumId.tsx` `buyRest`): the
  price is the **sum of the caller's not-yet-owned, for-sale album tracks at each track's individual
  price, NO bundle discount**, and exactly those tracks are granted. **Fix:** `CatalogExpansionReader`
  gains `remainingForSaleTracks(account, albumId)` (ownership-aware, via commerce's `OwnershipRepository`
  + catalog's `track` rows filtered to `for-sale`, positive price, not-owned); `CheckoutService` prices
  the `album-rest` line as their sum (rejecting with 404 `PriceUnavailable` if the caller owns everything —
  nothing to buy); album-rest expansion (`tracksToGrant`) returns only the album's for-sale tracks so the
  grant fan-out (with its per-track already-owned guard) grants exactly the remaining tracks. Proven by
  `CheckoutServiceTest.checkout_albumRest_partialOwnership_chargesSumOfRemainingTracks_notAlbumPrice`,
  `checkout_albumRest_ownsEverything_rejected` (unit), and
  `CheckoutFlowIT.checkout_albumRest_partialOwnership_chargesRemainingTracks_grantsRemaining` (integration).
- **F3 — corrected comments.** The "REQUIRES_NEW rolls back / benign no-op" comments in
  `LedgerPostingService.claimPosting` and `PaymentsSaleLedgerPosterAdapter` were corrected to state that
  the DB 23505 marks the *current* transaction rollback-only, so isolation is the CALLER's responsibility
  (the caller must invoke on a `REQUIRES_NEW` boundary and pass a per-source-unique `refId`).

**ADR:** the per-creator ledger source ref + album-rest ownership-aware pricing are recorded as
**ADR-24** in `backend/docs/00-system-architecture.md` §9.

### 14.2 Code-review fixes (idempotency-key reuse; partial-split atomicity)

- **Review-A — same-key-different-body → 409.** The checkout idempotency short-circuit previously
  returned the stored order UNCONDITIONALLY on a key match. Per `api-and-contract.md §5.2` (every money
  POST), a replayed `Idempotency-Key` with a *different* request must be **409
  `IDEMPOTENCY_KEY_CONFLICT`**, never a silent stale-order return or a re-charge. `CheckoutService` now
  computes a SHA-256 `request_hash` over the idempotency-relevant fields — the cart contents (each line's
  `kind:refId:qty`, sorted so ordering is irrelevant) plus `paymentMethodId` — persists it on the order
  (`request_hash` column, V944), and on a key match compares it: **same hash → idempotent replay**
  (return the stored order, one charge); **different hash → 409** (`commerce.domain.IdempotencyConflictException`,
  reusing the existing `IDEMPOTENCY_KEY_CONFLICT` code, 409). The re-priced amount is intentionally NOT
  hashed (server-side re-pricing means the client never supplies it; the cart contents + method fully
  identify the request). Mirrors payments' `InitiateChargeService`. Proven by
  `CheckoutServiceTest.checkout_sameKeyDifferentPaymentMethod_throwsConflict409`,
  `checkout_sameKeyDifferentCart_throwsConflict409` (unit) and
  `CheckoutFlowIT.checkout_sameKeyDifferentBody_returns409_IdempotencyReuse` (integration).
- **Review-B — per-creator-split partial-failure atomicity (no double-credit; KEPT design).** Reviewers
  asked what happens if creator #2's split fails with a NON-duplicate error after creator #1's
  `REQUIRES_NEW` split committed. A regression test proves it: the outer grant transaction rolls back
  (order stays `pending`, grants + `order_grant_posting` claim released) while creator #1's credit
  survives; on re-delivery the grant re-runs and creator #1's re-post hits its already-committed
  `ledger_posting` PK → `DuplicatePostingException` → swallowed by the adapter → **creator #1 is NOT
  double-credited**; creator #2 (transient failure cleared) posts once. **End state: each creator
  credited exactly once, both tracks granted once, order `paid`, ledger balanced — no double-credit, no
  imbalance.** The design is KEPT; the only residual is an *orphaned creator credit* on a PERMANENT split
  failure (mitigation — SAVEPOINT-per-split or an outbox/compensation — deferred, see ADR-24 addendum).
  Proven by `GrantOwnershipServiceTest.grant_multiCreatorPartialSplitFailureThenRetry_noDoubleCredit_grantedOnce_B`.
  Note: `FakeSaleLedgerPoster` was corrected to model the port contract precisely — a duplicate
  `(intent, refId)` is a benign no-op the CALLER never sees (matching the real adapter's swallow), so the
  F1 same-ref regression now surfaces as a missing posting (distinct-ref count assertion fails) rather
  than a thrown exception.

## 15. WU-COM-3 implementation notes (order detail + line fidelity, shipped)

**Single-order lookup.** `GET /v1/me/orders/{orderId}` was added (`GetOrder` port + `GetOrderService`,
`OrderResource.get`) so the frontend checkout flow can poll a specific order's status after `POST
/v1/checkout`'s `202` — `GET /v1/me/orders` only ever listed, never fetched by id. Ownership is checked
in the service (`order.getAccountId().equals(account)`); a missing id and someone else's order both throw
the SAME `OrderNotFoundException` → 404, so the endpoint never confirms another account's order exists
(§2.2).

**Order-line display fidelity.** `order_line` gained nullable `subtitle`/`image` columns
(`V968__commerce_order_line_display_fields.sql`), copied from the cart's priced item at checkout
(`PricedItem.subtitle()`/`.image()`, already resolved by `PricingService` for every kind) alongside the
existing `title` copy. Before this, a settled order's receipt had nowhere to source line artwork —
`cart_item` had carried `subtitle`/`image` since `V943`, but `order_line` never did. `OrderLine`'s
constructor gained the two fields (nullable, no validation, matching `CartItem`'s own nullable
subtitle/image); the three existing call sites (`OrderEntityMapper.toDomain`, `CheckoutService`'s two
`OrderLine` construction sites) and two test fixture files (`OrderTest`, `GrantOwnershipServiceTest`)
were updated. Proven by `CheckoutServiceTest.checkout_pricedLine_carriesDisplaySubtitleAndImage` (unit)
and `CheckoutFlowIT.getOrder_ownOrder_returns200WithDisplayFields` (integration).

Enables slice 3 of the frontend->backend wiring program
(`docs/superpowers/specs/2026-07-15-commerce-checkout-wiring-design.md`) — the checkout receipt page
polls `GET /v1/me/orders/{orderId}` until settlement instead of asserting success client-side.
