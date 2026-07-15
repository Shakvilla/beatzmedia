# Slice 3 — Commerce Checkout Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire BeatzClik's cart → checkout → order-settlement → ownership flow from a client-only mock to the real Quarkus backend, with no visual change, honoring that MoMo settlement is asynchronous.

**Architecture:** Two work units, two PRs, in dependency order. **Part A (backend, `WU-COM-3`)** adds a single-order lookup endpoint and closes a receipt-fidelity gap (`order_line` can't carry cart artwork). **Part B (frontend)** rewrites `CartProvider` to run dual-mode (local for guests, TanStack Query-backed for signed-in users, merging on login), makes checkout an async submit against the real `POST /v1/checkout`, and rewrites the receipt page to poll the new single-order endpoint until settlement — mirroring the loader/`useSuspenseQuery` pattern from slice 1 and the optimistic-mutation pattern from slice 2b.

**Tech Stack:** Java 25 / Quarkus 3.37.x / JAX-RS / PostgreSQL+Flyway / JUnit5 + REST-assured + Testcontainers (backend); React 19 / TanStack Router+Query 5 / TypeScript / Vitest (frontend).

## Global Constraints

- Money in integer minor units (pesewas) internally; API money shape is always `{ amount, currency: "GHS" }` (INV-11).
- Hexagonal dependency rule: adapter → application → domain; domain types carry no framework imports (ArchUnit-enforced).
- Forward-only Flyway migrations, named `V<n>__<snake_desc>.sql`; allocate the version with `bash backend/scripts/next-migration-version.sh` **at implementation time** (968 was next-free when this plan was written — re-run the script before creating the file in case other work has landed).
- A resource that isn't the caller's own returns **404, never 403** (§2.2) — existence of another account's resource is never confirmed.
- Idempotency-Key is required on every money/side-effect POST (INV already enforced server-side for `/v1/checkout`; Part B must generate and send one).
- Every privileged mutation appends exactly one `AuditEntry` (INV-10) — neither part of this plan adds a new privileged mutation, so no new audit call sites are needed.
- Node 22 is required for all frontend commands (the default shell `node` is v10 and crashes Vitest/Vite): prefix frontend commands with `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH"`.
- The Vite dev-server proxy must target `http://127.0.0.1:8080`, not `localhost:8080` (an unrelated app squats on `::1:8080`).
- Definition of Done per WU: unit + integration + contract tests pass; ArchUnit green; Flyway migrations apply on an empty DB; `docker compose up` boots healthy; coverage gate met; Spotless clean; the module ADD (`backend/docs/architecture/commerce.md`) is updated in the same PR.
- The backend verification gate (`bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh`) is run **by the user**, not the agent — the agent stops short of running it (IntelliJ JPS races the build) and asks the user to run it and report results.
- Commit after each completed step group (failing test written, implementation passing, docs) — not one giant commit at the end.

---

# Part A — Backend: WU-COM-3 (order detail + line fidelity)

Branch: `feat/WU-COM-3-order-detail-and-line-fidelity`, created off `origin/master` (already includes slice 2b, PR #124).

## Task A1: Register WU-COM-3 and WU-COM-4 in the backlog

**Files:**
- Modify: `backend/.project/backlog.yaml`

**Interfaces:**
- Produces: nothing consumed by later tasks — this is bookkeeping so `/status` and the autonomous loop can see the work.

- [ ] **Step 1: Add the two backlog entries**

Open `backend/.project/backlog.yaml` and find the `WU-COM-2` entry (search for `id: WU-COM-2`). Insert two new entries immediately after its closing comment block and before the `WU-PLY-1` entry:

```yaml
  - id: WU-COM-3
    title: Single-order lookup + order-line display fidelity (subtitle/image)
    phase: 2
    module: commerce
    add: commerce.md
    owner: backend-engineer
    depends_on: [WU-COM-2]
    llfrs: [LLFR-COMMERCE-02.4]
    status: todo
    # Frontend slice 3 (docs/superpowers/specs/2026-07-15-commerce-checkout-wiring-design.md) needs to
    # poll a single order by id after POST /v1/checkout's 202 to detect settlement (pending -> paid /
    # fulfilled / failed) — GET /v1/me/orders only lists, it does not fetch by id. Also closes a real
    # gap: order_line has no subtitle/image columns (cart_item does, V943), so a settled order's
    # receipt cannot render the same artwork the cart showed. Both are additive; no invariant changes.
  - id: WU-COM-4
    title: Authoritative pricing for episode/season-pass/ticket/store + card hosted-checkout redirect
    phase: 3
    module: commerce
    add: commerce.md
    owner: payments-specialist
    depends_on: [WU-COM-3, WU-STO-1, WU-EVT-1, WU-POD-1]
    llfrs: []
    status: todo
    # NOT built by slice 3 — registered so the gap is tracked. CheckoutService.gateKind() already
    # hard-rejects episode/season-pass/ticket/store with 409 CHECKOUT_KIND_UNSUPPORTED (ADR-23 safe
    # default) because their only pricing source today is client-supplied cart metadata
    # (CatalogPricingServiceAdapter.priceFromMetadata) — not a live exploit (checkout blocks it), but a
    # feature gap now that the owning modules (store/events/podcasts) have shipped real catalogs. Also
    # covers the card hosted-checkout redirect (checkoutUrl, added to PaymentIntentView by WU-PAY-6 but
    # unreachable through CheckoutResult) — both are gated behind the Redde credential human gate
    # (PSP_REDDE stays off until real credentials are supplied) and should land together before go-live.
```

- [ ] **Step 2: Commit**

```bash
git add backend/.project/backlog.yaml
git commit -m "chore(commerce): register WU-COM-3 and WU-COM-4 in the backlog"
```

---

## Task A2: `order_line` gains `subtitle`/`image` (migration + domain + persistence + service wiring)

**Files:**
- Create: `backend/src/main/resources/db/migration/V968__commerce_order_line_display_fields.sql` (confirm the version number with `next-migration-version.sh` first)
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderLine.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderLineEntity.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderEntityMapper.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderLineView.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderSnapshot.java`
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/CheckoutService.java`
- Modify (existing tests broken by the constructor change): `backend/src/test/java/org/shakvilla/beatzmedia/commerce/domain/OrderTest.java`, `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GrantOwnershipServiceTest.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/CheckoutServiceTest.java` (new assertion, existing file)

**Interfaces:**
- Consumes: `PricedItem(String title, String subtitle, String image, Money unitPrice)` (`commerce/application/port/out/PricedItem.java`, already carries subtitle/image — no change needed there).
- Produces: `OrderLine` constructor becomes `OrderLine(String id, CartItemKind kind, String refId, String title, String subtitle, String image, Money unitPrice, int qty)` — `subtitle`/`image` are nullable (no validation), inserted right after `title`. `OrderLine.getSubtitle(): String` / `OrderLine.getImage(): String` are new getters. `OrderLineView` gains `subtitle`/`image` fields in the same position. Later tasks (A3+) read these getters.

### Step 1: Write the failing test proving the plumbing

In `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/CheckoutServiceTest.java`, add this test immediately after `checkout_tamperedCartPrice_chargesTrueServerPrice_notClientAmount` (which already asserts on `orders.all().get(0).getLines().get(0)`):

```java
  @Test
  void checkout_pricedLine_carriesDisplaySubtitleAndImage() {
    // FakePricingService.seed() always sets subtitle="Subtitle", image="img.jpg" (see fakes/FakePricingService).
    seedCartWithStoredPrice(CartItemKind.track, "t1", 500);
    pricing.seed(CartItemKind.track, "t1", "Real Track", 500);

    service.checkout(ACCOUNT, KEY, "mtn");

    org.shakvilla.beatzmedia.commerce.domain.OrderLine line = orders.all().get(0).getLines().get(0);
    assertEquals("Subtitle", line.getSubtitle(), "order line snapshots the priced item's subtitle");
    assertEquals("img.jpg", line.getImage(), "order line snapshots the priced item's image");
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CheckoutServiceTest#checkout_pricedLine_carriesDisplaySubtitleAndImage`
Expected: **compilation failure** — `OrderLine.getSubtitle()` and `OrderLine.getImage()` do not exist yet.

- [ ] **Step 3: Write the migration**

Re-run `bash backend/scripts/next-migration-version.sh` to confirm the version, then create the file (adjust the number if it has drifted from 968):

```sql
-- V968__commerce_order_line_display_fields.sql
-- WU-COM-3: order_line gains subtitle/image (nullable, additive) so a settled order's receipt can
-- render the same display data the cart already carries (cart_item.subtitle/.image, V943). Copied
-- from the cart's priced item at checkout time (CheckoutService), alongside the existing title copy.
-- No existing column changes; no data backfill needed (existing rows get NULL, which the frontend
-- already treats as "no subtitle/no image" — same as any other nullable display field).

ALTER TABLE order_line ADD COLUMN subtitle VARCHAR(256);
ALTER TABLE order_line ADD COLUMN image VARCHAR(512);
```

- [ ] **Step 4: Update the `OrderLine` domain class**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderLine.java`, replace the whole file:

```java
package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A single order line — an immutable price snapshot taken at checkout. Entity within the {@link Order}
 * aggregate. Once checkout snapshots it, the price is NEVER re-derived (Commerce ADD §3, §12.2).
 * {@code subtitle}/{@code image} are display-only snapshots of the priced item (WU-COM-3) — nullable,
 * no validation, mirroring {@code CartItem}'s own nullable subtitle/image. Domain-layer; no framework
 * imports.
 */
public final class OrderLine {

  private final String id;
  private final CartItemKind kind;
  private final String refId;
  private final String title;
  private final String subtitle;
  private final String image;
  private final Money unitPrice;
  private final int qty;

  public OrderLine(
      String id,
      CartItemKind kind,
      String refId,
      String title,
      String subtitle,
      String image,
      Money unitPrice,
      int qty) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("OrderLine id must not be blank");
    }
    if (kind == null) {
      throw new IllegalArgumentException("OrderLine kind must not be null");
    }
    if (refId == null || refId.isBlank()) {
      throw new IllegalArgumentException("OrderLine refId must not be blank");
    }
    if (unitPrice == null) {
      throw new IllegalArgumentException("OrderLine unitPrice must not be null");
    }
    if (qty < 1) {
      throw new IllegalArgumentException("OrderLine qty must be >= 1");
    }
    this.id = id;
    this.kind = kind;
    this.refId = refId;
    this.title = title;
    this.subtitle = subtitle;
    this.image = image;
    this.unitPrice = unitPrice;
    this.qty = qty;
  }

  public String getId() {
    return id;
  }

  public CartItemKind getKind() {
    return kind;
  }

  public String getRefId() {
    return refId;
  }

  public String getTitle() {
    return title;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public String getImage() {
    return image;
  }

  public Money getUnitPrice() {
    return unitPrice;
  }

  public int getQty() {
    return qty;
  }

  /** Line total = unitPrice × qty (INV-11, minor units). */
  public Money lineTotal() {
    return Money.ofMinor(unitPrice.minor() * qty, unitPrice.currency());
  }
}
```

- [ ] **Step 5: Update `OrderLineEntity`**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderLineEntity.java`, add two nullable columns after `title`:

```java
  @Column(name = "title", nullable = false)
  public String title;

  @Column(name = "subtitle")
  public String subtitle;

  @Column(name = "image")
  public String image;

  @Column(name = "unit_price_minor", nullable = false)
  public long unitPriceMinor;
```

- [ ] **Step 6: Update `OrderEntityMapper`**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderEntityMapper.java`, update `toDomain` (add `l.subtitle, l.image` after `l.title`):

```java
  Order toDomain(OrderEntity e) {
    Currency currency = Currency.valueOf(e.currency);
    List<OrderLine> lines =
        e.lines.stream()
            .map(
                l ->
                    new OrderLine(
                        l.id,
                        CartItemKind.fromWireValue(l.kind),
                        l.refId,
                        l.title,
                        l.subtitle,
                        l.image,
                        Money.ofMinor(l.unitPriceMinor, Currency.valueOf(l.currency)),
                        l.qty))
            .toList();
    return new Order(
        new OrderId(e.id),
        new AccountId(e.accountId),
        e.reference,
        OrderStatus.valueOf(e.status),
        Money.ofMinor(e.subtotalMinor, currency),
        Money.ofMinor(e.feeMinor, currency),
        Money.ofMinor(e.totalMinor, currency),
        e.paymentIntentId,
        e.failureReason,
        e.idempotencyKey,
        e.requestHash,
        lines,
        e.createdAt);
  }
```

And `toEntity`'s line-reconciliation loop (add two field assignments after `le.title = line.getTitle();`):

```java
    for (OrderLine line : order.getLines()) {
      OrderLineEntity le = existingById.get(line.getId());
      if (le == null) {
        le = new OrderLineEntity();
        le.id = line.getId();
      }
      le.order = entity;
      le.kind = line.getKind().wireValue();
      le.refId = line.getRefId();
      le.title = line.getTitle();
      le.subtitle = line.getSubtitle();
      le.image = line.getImage();
      le.unitPriceMinor = line.getUnitPrice().minor();
      le.currency = line.getUnitPrice().currency().name();
      le.qty = line.getQty();
      reconciled.add(le);
    }
```

- [ ] **Step 7: Update `OrderLineView` and `OrderSnapshot.of`**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderLineView.java`, replace the record:

```java
package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Wire shape for one order line, including the display snapshot taken at checkout ({@code
 * subtitle}/{@code image} — nullable, WU-COM-3). API-CONTRACT.md §6.
 */
public record OrderLineView(
    String id,
    String kind,
    String refId,
    String title,
    String subtitle,
    String image,
    MoneyView unitPrice,
    int quantity) {}
```

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderSnapshot.java`, update the line-mapping lambda inside `of(Order order)`:

```java
    List<OrderLineView> lines =
        order.getLines().stream()
            .map(
                l ->
                    new OrderLineView(
                        l.getId(),
                        l.getKind().wireValue(),
                        l.getRefId(),
                        l.getTitle(),
                        l.getSubtitle(),
                        l.getImage(),
                        MoneyView.ofMinor(l.getUnitPrice().minor(), currency),
                        l.getQty()))
            .toList();
```

- [ ] **Step 8: Wire `CheckoutService` to pass subtitle/image into the two `OrderLine` construction sites**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/CheckoutService.java`, update the per-item line builder (inside the `for (CartItem item : cart.getItems())` loop):

```java
      // Authoritative server-side re-price — the cart-stored price is NEVER trusted (INV-11 / G1).
      PricedItem priced = pricingService.priceFor(kind, item.getRefId(), item.getMetadata());
      lines.add(
          new OrderLine(
              ids.newId(),
              kind,
              item.getRefId(),
              priced.title(),
              priced.subtitle(),
              priced.image(),
              priced.unitPrice(),
              item.getQty()));
```

And `albumRestLine`:

```java
  private OrderLine albumRestLine(AccountId account, CartItem item, Currency currency) {
    List<CatalogExpansionReader.PurchasableTrack> remaining =
        expansionReader.remainingForSaleTracks(account, item.getRefId());
    if (remaining.isEmpty()) {
      throw new PriceUnavailableException("album-rest", item.getRefId());
    }
    long totalMinor = remaining.stream().mapToLong(CatalogExpansionReader.PurchasableTrack::priceMinor).sum();
    // Title/subtitle/image come from the interim priced metadata (display only); the PRICE is the
    // authoritative server-computed sum above, never the client/cart value (G1/INV-11).
    PricedItem display = pricingService.priceFor(CartItemKind.album, item.getRefId(), item.getMetadata());
    return new OrderLine(
        ids.newId(),
        CartItemKind.album_rest,
        item.getRefId(),
        display.title(),
        display.subtitle(),
        display.image(),
        Money.ofMinor(totalMinor, currency),
        1);
  }
```

- [ ] **Step 9: Fix the two existing test files broken by the constructor signature change**

In `backend/src/test/java/org/shakvilla/beatzmedia/commerce/domain/OrderTest.java`, update `pendingOrder()`:

```java
  private Order pendingOrder() {
    OrderLine line1 =
        new OrderLine(
            "l1", CartItemKind.track, "t1", "Track 1", "Artist 1", "img1.jpg",
            Money.ofMinor(500, Currency.GHS), 1);
    OrderLine line2 =
        new OrderLine(
            "l2", CartItemKind.track, "t2", "Track 2", "Artist 2", "img2.jpg",
            Money.ofMinor(750, Currency.GHS), 1);
    return Order.create(
        new OrderId("o1"),
        ACCOUNT,
        "BZ-2026-00001",
        List.of(line1, line2),
        Money.ofMinor(50, Currency.GHS),
        Currency.GHS,
        NOW);
  }
```

In `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GrantOwnershipServiceTest.java`, find the two `new OrderLine(` call sites (lines 90 and 94) and update them:

```java
  private static OrderLine trackLine(String trackId, long priceMinor) {
    return new OrderLine(
        "l-" + trackId, CartItemKind.track, trackId, "Track", "Artist", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  private static OrderLine albumLine(String albumId, long priceMinor) {
    return new OrderLine(
        "l-" + albumId, CartItemKind.album, albumId, "Album", "Artist", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), 1);
  }
```

(Match whatever the surrounding method names/signatures already are in that file — only the `new OrderLine(...)` argument lists change; do not rename the helper methods.)

- [ ] **Step 10: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CheckoutServiceTest,OrderTest,GrantOwnershipServiceTest`
Expected: PASS (all three files compile and pass, including the new `checkout_pricedLine_carriesDisplaySubtitleAndImage` test).

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/resources/db/migration/V968__commerce_order_line_display_fields.sql \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderLine.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderLineEntity.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/OrderEntityMapper.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderLineView.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/OrderSnapshot.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/CheckoutService.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/domain/OrderTest.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GrantOwnershipServiceTest.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/CheckoutServiceTest.java
git commit -m "feat(commerce): WU-COM-3 order_line carries subtitle/image (receipt fidelity)"
```

---

## Task A3: `GetOrder` port + service — single order by id, scoped to the caller

**Files:**
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderNotFoundException.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/GetOrder.java`
- Create: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/GetOrderService.java`
- Test: `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GetOrderServiceTest.java` (new)

**Interfaces:**
- Consumes: `OrderLine(id, kind, refId, title, subtitle, image, unitPrice, qty)` (Task A2), `OrderRepository.findById(OrderId): Optional<Order>` (already exists, unchanged), `OrderSnapshot.of(Order): OrderSnapshot` (already exists, unchanged apart from A2's line fields), `FakeOrderRepository` (already exists at `commerce/fakes/FakeOrderRepository.java`, unchanged).
- Produces: `GetOrder.getOrder(AccountId account, OrderId orderId): OrderSnapshot`, throwing `OrderNotFoundException` (extends `platform.domain.NotFoundException`, maps to HTTP 404 via the existing generic `ErrorCode.NOT_FOUND` mapping — no `ErrorCode`/`DomainExceptionMapper` change needed, mirroring `CartLineNotFoundException`). Task A4 wires this port into the REST layer.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GetOrderServiceTest.java`:

```java
package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.application.service.GetOrderService;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OrderNotFoundException;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for {@link GetOrderService} (WU-COM-3, {@code GET /v1/me/orders/{orderId}}). Proves
 * the not-yours-is-404 convention (§2.2): a missing order and someone else's order both throw the
 * SAME exception, so the endpoint never confirms another account's order exists.
 */
@Tag("unit")
class GetOrderServiceTest {

  private static final AccountId OWNER = new AccountId("acct-owner");
  private static final AccountId STRANGER = new AccountId("acct-stranger");
  private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");

  private final FakeOrderRepository repo = new FakeOrderRepository();
  private final GetOrderService getOrder = new GetOrderService(repo);

  private void seedOrder() {
    OrderLine line =
        new OrderLine(
            "l1", CartItemKind.track, "t1", "Track 1", "Artist 1", "img.jpg",
            Money.ofMinor(500, Currency.GHS), 1);
    Order order =
        Order.create(
            new OrderId("o1"), OWNER, "BZ-2026-00001", List.of(line),
            Money.ofMinor(50, Currency.GHS), Currency.GHS, NOW);
    repo.save(order);
  }

  @Test
  void getOrder_ownOrder_returnsSnapshotWithDisplayFields() {
    seedOrder();

    OrderSnapshot snapshot = getOrder.getOrder(OWNER, new OrderId("o1"));

    assertEquals("o1", snapshot.orderId());
    assertEquals("BZ-2026-00001", snapshot.reference());
    assertEquals(1, snapshot.items().size());
    assertEquals("Artist 1", snapshot.items().get(0).subtitle());
    assertEquals("img.jpg", snapshot.items().get(0).image());
  }

  @Test
  void getOrder_unknownId_throwsOrderNotFound() {
    assertThrows(
        OrderNotFoundException.class, () -> getOrder.getOrder(OWNER, new OrderId("no-such-order")));
  }

  @Test
  void getOrder_someoneElsesOrder_throwsSameOrderNotFound_notForbidden() {
    seedOrder();

    assertThrows(
        OrderNotFoundException.class, () -> getOrder.getOrder(STRANGER, new OrderId("o1")));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GetOrderServiceTest`
Expected: compilation failure — `GetOrderService`, `GetOrder`, and `OrderNotFoundException` do not exist yet.

- [ ] **Step 3: Write `OrderNotFoundException`**

Create `backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderNotFoundException.java`:

```java
package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.NotFoundException;

/**
 * Thrown when an order id does not exist, OR exists but belongs to a different account. Both
 * cases return the SAME 404 (§2.2 not-yours-is-404) — existence of another account's order is
 * never confirmed. Mirrors {@link CartLineNotFoundException}.
 */
public class OrderNotFoundException extends NotFoundException {

  public OrderNotFoundException(String orderId) {
    super("Order not found: " + orderId);
  }
}
```

- [ ] **Step 4: Write the `GetOrder` port**

Create `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/GetOrder.java`:

```java
package org.shakvilla.beatzmedia.commerce.application.port.in;

import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port for {@code GET /v1/me/orders/{orderId}} — a single order by id, scoped to the
 * caller's own orders (WU-COM-3, follow-up to LLFR-COMMERCE-02.4). The frontend polls this after
 * {@code POST /v1/checkout}'s {@code 202} to detect settlement (pending → paid/fulfilled/failed).
 * Not-yours-or-missing both 404 (§2.2).
 */
public interface GetOrder {

  OrderSnapshot getOrder(AccountId account, OrderId orderId);
}
```

- [ ] **Step 5: Write `GetOrderService`**

Create `backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/GetOrderService.java`:

```java
package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.commerce.application.port.in.GetOrder;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Application service for {@code GET /v1/me/orders/{orderId}} ({@link GetOrder}, WU-COM-3). Loads
 * by id and asserts caller ownership before returning; both "no such order" and "someone else's
 * order" throw the SAME {@link OrderNotFoundException} (§2.2).
 */
@ApplicationScoped
public class GetOrderService implements GetOrder {

  private final OrderRepository orderRepository;

  @Inject
  public GetOrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Override
  @Transactional
  public OrderSnapshot getOrder(AccountId account, OrderId orderId) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId.value()));
    if (!order.getAccountId().equals(account)) {
      throw new OrderNotFoundException(orderId.value());
    }
    return OrderSnapshot.of(order);
  }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GetOrderServiceTest`
Expected: PASS (3/3).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/commerce/domain/OrderNotFoundException.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/in/GetOrder.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/service/GetOrderService.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/application/GetOrderServiceTest.java
git commit -m "feat(commerce): WU-COM-3 GetOrder port + service"
```

---

## Task A4: `GET /v1/me/orders/{orderId}` REST wiring + integration tests

**Files:**
- Modify: `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/in/rest/OrderResource.java`
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CheckoutFlowIT.java` (append tests, reusing its existing fixtures/helpers)

**Interfaces:**
- Consumes: `GetOrder.getOrder(AccountId, OrderId): OrderSnapshot` (Task A3).
- Produces: `GET /v1/me/orders/{orderId}` → `200 OrderSnapshot` | `404` (missing or not-yours) | `401` (no token). Task A5 (contract test) and Part B (frontend `orderQuery`) consume this endpoint.

- [ ] **Step 1: Write the failing integration tests**

In `backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CheckoutFlowIT.java`, add these tests right after the existing `listOrders_returnsOwnOrdersNewestFirst` test (before the `// ---- helpers ----` comment):

```java
  // ---- GET /v1/me/orders/{orderId} (WU-COM-3) ------------------------------------

  @Test
  void getOrder_ownOrder_returns200WithDisplayFields() {
    String token = signUp("co3-get-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    Response co = checkout(token, "co3-getkey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("orderId", equalTo(orderId))
        .body("status", equalTo("pending"))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].subtitle", notNullValue())
        .body("items[0].image", notNullValue());
  }

  @Test
  void getOrder_afterSettlement_statusIsPaid() {
    String token = signUp("co3-settle-" + System.nanoTime() + "@example.com");
    addToCart(token, "track", trackId);
    Response co = checkout(token, "co3-settlekey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");
    String intentId = co.jsonPath().getString("paymentIntentId");

    settle(intentId, "co3-settle-ev-" + System.nanoTime());

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("status", equalTo("paid"));
  }

  @Test
  void getOrder_unknownId_returns404() {
    String token = signUp("co3-unknown-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/no-such-order-xyz")
        .then()
        .statusCode(404);
  }

  @Test
  void getOrder_someoneElsesOrder_returns404_notForbidden() {
    String ownerToken = signUp("co3-owner-" + System.nanoTime() + "@example.com");
    addToCart(ownerToken, "track", trackId);
    Response co = checkout(ownerToken, "co3-strangerkey-" + System.nanoTime());
    String orderId = co.then().statusCode(202).extract().jsonPath().getString("orderId");

    String strangerToken = signUp("co3-stranger-" + System.nanoTime() + "@example.com");

    given()
        .header("Authorization", "Bearer " + strangerToken)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(404);
  }

  @Test
  void getOrder_withoutToken_returns401() {
    given().when().get("/v1/me/orders/some-id").then().statusCode(401);
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw verify -Dtest=CheckoutFlowIT -DskipUTs=true` (or your usual IT command)
Expected: FAIL with `404 Not Found` from JAX-RS on every new test — `OrderResource` has no `{orderId}` path yet.

- [ ] **Step 3: Add the REST method**

In `backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/in/rest/OrderResource.java`, replace the whole file:

```java
package org.shakvilla.beatzmedia.commerce.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.shakvilla.beatzmedia.commerce.application.port.in.GetOrder;
import org.shakvilla.beatzmedia.commerce.application.port.in.ListOrders;
import org.shakvilla.beatzmedia.commerce.application.port.in.OrderSnapshot;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

import io.quarkus.security.Authenticated;

/**
 * Thin REST resource for order history and order detail (LLFR-COMMERCE-02.4, WU-COM-3). Both
 * endpoints are scoped to the caller's OWN orders (by JWT subject); another account's orders are
 * never visible — a foreign or missing order id is 404, never 403 (§2.2). Commerce ADD §5.1 / §15
 * / API-CONTRACT.md §6.
 */
@Path("/v1/me/orders")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class OrderResource {

  private final ListOrders listOrders;
  private final GetOrder getOrder;
  private final JsonWebToken jwt;

  @Inject
  public OrderResource(ListOrders listOrders, GetOrder getOrder, JsonWebToken jwt) {
    this.listOrders = listOrders;
    this.getOrder = getOrder;
    this.jwt = jwt;
  }

  @GET
  public Page<OrderSnapshot> list(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("20") int size) {
    return listOrders.listOrders(new AccountId(jwt.getSubject()), new PageRequest(page, size));
  }

  @GET
  @Path("/{orderId}")
  public OrderSnapshot get(@PathParam("orderId") String orderId) {
    return getOrder.getOrder(new AccountId(jwt.getSubject()), new OrderId(orderId));
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./mvnw verify -Dtest=CheckoutFlowIT -DskipUTs=true`
Expected: PASS, all tests in `CheckoutFlowIT` including the 5 new ones.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/in/rest/OrderResource.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CheckoutFlowIT.java
git commit -m "feat(commerce): WU-COM-3 GET /v1/me/orders/{orderId}"
```

---

## Task A5: Contract test — order shape matches the frontend

**Files:**
- Modify: `backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CommerceContractTest.java`

**Interfaces:**
- Consumes: `GET /v1/me/orders/{orderId}` (Task A4).
- Produces: nothing consumed by later tasks — this is the contract-conformance gate for Part B.

- [ ] **Step 1: Write the failing test**

In `backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CommerceContractTest.java`, add this test after `cart_item_response_has_required_fields_matching_frontend_CartItem` (reuse the file's existing `seedTrack()` helper and `fan()` helper — both already exist per the file's current tests):

```java
  @Test
  void order_response_has_required_fields_matching_frontend_OrderSnapshot() {
    String token = fan();
    seedTrack();

    given()
        .header("Authorization", "Bearer " + token)
        .contentType(ContentType.JSON)
        .body("""
            { "kind": "track", "refId": "%s" }
            """.formatted(TRACK_ID))
        .when()
        .post("/v1/me/cart/items")
        .then()
        .statusCode(200);

    String orderId =
        given()
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", "contract-order-key-" + System.nanoTime())
            .contentType(ContentType.JSON)
            .body("""
                { "paymentMethodId": "mtn" }
                """)
            .when()
            .post("/v1/checkout")
            .then()
            .statusCode(202)
            .extract()
            .jsonPath()
            .getString("orderId");

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/v1/me/orders/" + orderId)
        .then()
        .statusCode(200)
        .body("orderId", isA(String.class))
        .body("reference", isA(String.class))
        .body("status", isA(String.class))
        .body("subtotal.amount", isA(Number.class))
        .body("subtotal.currency", equalTo("GHS"))
        .body("fee.amount", isA(Number.class))
        .body("total.amount", isA(Number.class))
        .body("items[0].id", isA(String.class))
        .body("items[0].kind", equalTo("track"))
        .body("items[0].refId", equalTo(TRACK_ID))
        .body("items[0].title", isA(String.class))
        .body("items[0].unitPrice.amount", isA(Number.class))
        .body("items[0].unitPrice.currency", equalTo("GHS"))
        .body("items[0].quantity", isA(Integer.class));
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./mvnw verify -Dtest=CommerceContractTest -DskipUTs=true`
Expected: FAIL if run before Task A4 lands (404 on the new path); if run after A4 this should already PASS — run it now purely to confirm the contract holds, treating a pass here as the "green" signal rather than a strict red-then-green cycle (the endpoint already exists from Task A4).

- [ ] **Step 3: Run again to confirm green**

Run: `cd backend && ./mvnw verify -Dtest=CommerceContractTest -DskipUTs=true`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/org/shakvilla/beatzmedia/commerce/it/CommerceContractTest.java
git commit -m "test(commerce): WU-COM-3 contract test for GET /v1/me/orders/{orderId}"
```

---

## Task A6: Docs — API-CONTRACT.md + commerce.md as-built note

**Files:**
- Modify: `API-CONTRACT.md`
- Modify: `backend/docs/architecture/commerce.md`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing — documentation only, required by the module DoD.

- [ ] **Step 1: Update `API-CONTRACT.md` §6**

In `API-CONTRACT.md`, find this line (around line 129):

```markdown
| GET | `/me/orders` | purchase history | `OrderSnapshot[]` |
```

Replace it with:

```markdown
| GET | `/me/orders` | purchase history | `OrderSnapshot[]` |
| GET | `/me/orders/:id` | single order by id (checkout settlement poll target) — 404 if missing or not the caller's | `OrderSnapshot` |
```

Then, immediately after the existing `> \`/checkout\` is what unlocks tracks...` note block (around line 132-143), add a new note:

```markdown
> **Order-line display fields (WU-COM-3).** `OrderSnapshot`'s line items gain `subtitle?: string | null`
> and `image?: string | null` — additive, nullable, snapshotting the same display data the cart already
> carried at checkout time. Orders placed before this WU shipped have `null` for both (never backfilled).
```

- [ ] **Step 2: Add the commerce.md as-built section**

In `backend/docs/architecture/commerce.md`, find the end of `### 14.2 Code-review fixes (idempotency-key reuse; partial-split atomicity)` (the last section in the file) and append a new top-level section:

```markdown

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

Enables slice 3 of the frontend→backend wiring program
(`docs/superpowers/specs/2026-07-15-commerce-checkout-wiring-design.md`) — the checkout receipt page
polls `GET /v1/me/orders/{orderId}` until settlement instead of asserting success client-side.
```

- [ ] **Step 3: Commit**

```bash
git add API-CONTRACT.md backend/docs/architecture/commerce.md
git commit -m "docs(commerce): WU-COM-3 API-CONTRACT.md + commerce ADD as-built note"
```

---

## Task A7: Verify gate + open PR

- [ ] **Step 1: Ask the user to run the verification gate**

Tell the user: "WU-COM-3 is implemented and committed on `feat/WU-COM-3-order-detail-and-line-fidelity`. Please run `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh` and report the results — I don't run this myself since IntelliJ JPS races the build." Wait for their report.

- [ ] **Step 2: Fix any failures the user reports, re-commit, ask them to re-run**

Repeat until the user reports a green gate.

- [ ] **Step 3: Open the PR**

Use the `open-pull-request` skill (or `gh pr create` directly) targeting `master`, following the existing PR template: link `WU-COM-3` / `LLFR-COMMERCE-02.4`, DoD checklist, test evidence (unit/integration/contract counts), migration note (`V968`, no data backfill), ADD updated in this PR.

```bash
gh pr create --base master --head feat/WU-COM-3-order-detail-and-line-fidelity \
  --title "feat(commerce): WU-COM-3 order detail + line fidelity" \
  --body "$(cat <<'EOF'
## Summary
- Adds `GET /v1/me/orders/{orderId}` (GetOrder port + service) — the polling target frontend slice 3 needs to detect checkout settlement.
- Adds nullable `order_line.subtitle`/`.image` (V968) so a settled order's receipt can render the same artwork the cart showed — closes a real gap (`cart_item` already had these, `order_line` never did).
- Registers WU-COM-3 (this PR) and WU-COM-4 (follow-up, not built here — authoritative pricing for episode/season-pass/ticket/store + card hosted-checkout redirect) in the backlog.

## Test plan
- [x] Unit: `GetOrderServiceTest` (3), `CheckoutServiceTest` (+1 new assertion), `OrderTest`/`GrantOwnershipServiceTest` (updated for the constructor change)
- [x] Integration: `CheckoutFlowIT` (+5 new tests: own order 200, settled status, unknown 404, someone-else's-order 404, no-token 401)
- [x] Contract: `CommerceContractTest` (+1: order response shape matches `OrderSnapshot`)
- [x] Migration V968 applies on an empty DB (Flyway)
- [x] ArchUnit, Spotless, coverage gate — per `verify.sh` (run by the user, see PR checks)

## Docs
- [x] `API-CONTRACT.md` §6 — new endpoint row + display-fields note
- [x] `backend/docs/architecture/commerce.md` §15 — as-built note
EOF
)"
```

- [ ] **Step 4: Watch CI and report back**

Once CI settles, report the result to the user (mirroring how slices 1/2a/2b were reported) and stop — do not begin Part B until the user confirms this PR is merged (Part B's `orderQuery`/checkout-detail work depends on the new endpoint existing on `master`).

---

# Part B — Frontend: cart → checkout → receipt

Branch: `feat/frontend-commerce-checkout` (already exists, has the spec commits — `git checkout feat/frontend-commerce-checkout && git pull origin master --rebase` or merge master in, to pick up WU-COM-3 once Part A is merged, before starting Task B1).

**Do not start Part B until Part A's PR is merged to `master`** — `orderQuery`/`apiCheckout`'s settlement poll and the `subtitle`/`image` order fields depend on it.

## Task B1: `lib/api/queries/commerce.ts` — wire types, mappers, API calls

**Files:**
- Create: `Frontend/src/lib/api/queries/commerce.ts`
- Test: `Frontend/src/lib/api/queries/commerce.test.ts`

**Interfaces:**
- Consumes: `apiFetch<T>(path, options)` (`Frontend/src/lib/api/client.ts`, already supports `method: 'GET'|'POST'|'PUT'|'PATCH'|'DELETE'` and `idempotencyKey`), `Money` type (`Frontend/src/types/index.ts`).
- Produces (consumed by Tasks B2-B4): `CartItemKind`, `CartItem`, `CartData`, `EMPTY_CART`, `CART_KEY`, `cartQuery()`, `parseLineId(id): {kind, refId}`, `apiAddCartItem(item): Promise<CartData>`, `apiUpdateCartItemQty(lineId, qty): Promise<CartData>`, `apiRemoveCartItem(lineId): Promise<CartData>`, `apiCheckout(paymentMethodId, idempotencyKey): Promise<CheckoutResultData>`, `CheckoutResultData`, `OrderData`, `OrderLine` (order-line shape, distinct name from cart's `CartItem`), `orderQuery(orderId)`.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/lib/api/queries/commerce.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiFetch } from '../client'
import {
  cartQuery,
  orderQuery,
  parseLineId,
  apiAddCartItem,
  apiUpdateCartItemQty,
  apiRemoveCartItem,
  apiCheckout,
  EMPTY_CART,
  type CartItem,
} from './commerce'

vi.mock('../client', () => ({ apiFetch: vi.fn() }))

describe('commerce id parsing (pure)', () => {
  it('parseLineId splits kind:refId on the FIRST colon only', () => {
    expect(parseLineId('track:t1')).toEqual({ kind: 'track', refId: 't1' })
    expect(parseLineId('ticket:some-event:VIP')).toEqual({ kind: 'ticket', refId: 'some-event:VIP' })
    expect(parseLineId('store:item-1:M')).toEqual({ kind: 'store', refId: 'item-1:M' })
  })

  it('parseLineId throws on a malformed id with no colon', () => {
    expect(() => parseLineId('no-colon-here')).toThrow()
  })
})

describe('commerce API calls', () => {
  beforeEach(() => vi.mocked(apiFetch).mockReset())

  const wireCart = {
    items: [
      {
        id: 'track:t1', kind: 'track', refId: 't1', title: 'Song', subtitle: 'Artist',
        image: 'img.jpg', price: { amount: 5, currency: 'GHS' }, quantity: 1, stackable: false,
        metadata: null,
      },
    ],
    subtotal: { amount: 5, currency: 'GHS' },
    fee: { amount: 0.5, currency: 'GHS' },
    total: { amount: 5.5, currency: 'GHS' },
    count: 1,
  }

  it('cartQuery fetches and maps the wire cart, defaulting missing subtitle/image', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      ...wireCart,
      items: [{ ...wireCart.items[0], subtitle: null, image: null }],
    })

    const result = await cartQuery().queryFn(expect.anything())

    expect(apiFetch).toHaveBeenCalledWith('/me/cart', undefined)
    expect(result.items[0].subtitle).toBeUndefined()
    expect(result.items[0].image).toBe('')
    expect(result.subtotal).toBe(5)
    expect(result.fee).toBe(0.5)
    expect(result.total).toBe(5.5)
    expect(result.count).toBe(1)
  })

  it('apiAddCartItem parses the line id and POSTs kind/refId/qty/metadata', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)
    const item: Omit<CartItem, 'quantity'> & { quantity?: number } = {
      id: 'ticket:evt-1:VIP', kind: 'ticket', title: 'VIP Ticket', subtitle: 'Venue',
      image: 'img.jpg', price: { amount: 100, currency: 'GHS' }, quantity: 2,
    }

    await apiAddCartItem(item)

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items', {
      method: 'POST',
      body: {
        kind: 'ticket',
        refId: 'evt-1:VIP',
        qty: 2,
        metadata: { title: 'VIP Ticket', subtitle: 'Venue', image: 'img.jpg', priceMinor: 10000 },
      },
    })
  })

  it('apiUpdateCartItemQty PATCHes qty by lineId', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)

    await apiUpdateCartItemQty('ticket:evt-1:VIP', 3)

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items/ticket:evt-1:VIP', {
      method: 'PATCH',
      body: { qty: 3 },
    })
  })

  it('apiRemoveCartItem DELETEs by lineId', async () => {
    vi.mocked(apiFetch).mockResolvedValue(wireCart)

    await apiRemoveCartItem('track:t1')

    expect(apiFetch).toHaveBeenCalledWith('/me/cart/items/track:t1', { method: 'DELETE' })
  })

  it('apiCheckout POSTs paymentMethodId with the Idempotency-Key header', async () => {
    const result = {
      orderId: 'o1', reference: 'BZ-2026-00001', paymentIntentId: 'pi1', status: 'pending',
    }
    vi.mocked(apiFetch).mockResolvedValue(result)

    const got = await apiCheckout('mtn', 'idem-key-1')

    expect(apiFetch).toHaveBeenCalledWith('/checkout', {
      method: 'POST',
      body: { paymentMethodId: 'mtn' },
      idempotencyKey: 'idem-key-1',
    })
    expect(got).toEqual(result)
  })

  it('orderQuery fetches and maps the wire order snapshot', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      orderId: 'o1', reference: 'BZ-2026-00001', status: 'paid',
      items: [{
        id: 'l1', kind: 'track', refId: 't1', title: 'Song', subtitle: 'Artist', image: 'img.jpg',
        unitPrice: { amount: 5, currency: 'GHS' }, quantity: 1,
      }],
      subtotal: { amount: 5, currency: 'GHS' }, fee: { amount: 0.5, currency: 'GHS' },
      total: { amount: 5.5, currency: 'GHS' }, createdAt: '2026-07-15T10:00:00Z',
    })

    const result = await orderQuery('o1').queryFn(expect.anything())

    expect(apiFetch).toHaveBeenCalledWith('/me/orders/o1', undefined)
    expect(result.status).toBe('paid')
    expect(result.items[0].subtitle).toBe('Artist')
    expect(result.total).toBe(5.5)
  })

  it('EMPTY_CART has zeroed totals and no items', () => {
    expect(EMPTY_CART).toEqual({ items: [], subtotal: 0, fee: 0, total: 0, count: 0 })
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx vitest run src/lib/api/queries/commerce.test.ts`
Expected: FAIL — `./commerce` module does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `Frontend/src/lib/api/queries/commerce.ts`:

```ts
import { queryOptions } from '@tanstack/react-query'
import { apiFetch } from '../client'
import type { Money } from '../../../types'

export const CART_KEY = ['cart'] as const

export type CartItemKind =
  | 'track'
  | 'album'
  | 'album-rest'
  | 'store'
  | 'episode'
  | 'season-pass'
  | 'ticket'

export interface CartItem {
  /** Stable line id, always `${kind}:${refId}` (refId may itself contain colons). */
  id: string
  kind: CartItemKind
  title: string
  subtitle?: string
  image: string
  price: Money
  quantity: number
  stackable?: boolean
}

export interface CartData {
  items: CartItem[]
  subtotal: number
  fee: number
  total: number
  count: number
}

export const EMPTY_CART: CartData = { items: [], subtotal: 0, fee: 0, total: 0, count: 0 }

interface CartItemWire {
  id: string
  kind: string
  refId: string
  title: string
  subtitle: string | null
  image: string | null
  price: Money
  quantity: number
  stackable: boolean
  metadata: Record<string, unknown> | null
}

interface CartViewWire {
  items: CartItemWire[]
  subtotal: Money
  fee: Money
  total: Money
  count: number
}

function toCartItem(w: CartItemWire): CartItem {
  return {
    id: w.id,
    kind: w.kind as CartItemKind,
    title: w.title,
    subtitle: w.subtitle ?? undefined,
    image: w.image ?? '',
    price: w.price,
    quantity: w.quantity,
    stackable: w.stackable,
  }
}

export function toCartData(w: CartViewWire): CartData {
  return {
    items: w.items.map(toCartItem),
    subtotal: w.subtotal.amount,
    fee: w.fee.amount,
    total: w.total.amount,
    count: w.count,
  }
}

export function cartQuery() {
  return queryOptions({
    queryKey: CART_KEY,
    queryFn: async () => toCartData(await apiFetch<CartViewWire>('/me/cart')),
  })
}

// ---- cart line id <-> {kind, refId} ----
// Mirrors the backend exactly: CartItem.lineIdFor(kind, refId) = kind.wireValue() + ":" + refId.
// refId may itself contain colons (e.g. ticket "evt-1:VIP"), so only the FIRST colon is a separator.

export function parseLineId(id: string): { kind: CartItemKind; refId: string } {
  const sep = id.indexOf(':')
  if (sep === -1) {
    throw new Error(`Invalid cart line id (expected "kind:refId"): ${id}`)
  }
  return { kind: id.slice(0, sep) as CartItemKind, refId: id.slice(sep + 1) }
}

// ---- raw API calls ----

export async function apiAddCartItem(
  item: Omit<CartItem, 'quantity'> & { quantity?: number },
): Promise<CartData> {
  const { kind, refId } = parseLineId(item.id)
  return toCartData(
    await apiFetch<CartViewWire>('/me/cart/items', {
      method: 'POST',
      body: {
        kind,
        refId,
        qty: item.quantity,
        metadata: {
          title: item.title,
          subtitle: item.subtitle,
          image: item.image,
          priceMinor: Math.round(item.price.amount * 100),
        },
      },
    }),
  )
}

export async function apiUpdateCartItemQty(lineId: string, qty: number): Promise<CartData> {
  return toCartData(
    await apiFetch<CartViewWire>(`/me/cart/items/${lineId}`, { method: 'PATCH', body: { qty } }),
  )
}

export async function apiRemoveCartItem(lineId: string): Promise<CartData> {
  return toCartData(await apiFetch<CartViewWire>(`/me/cart/items/${lineId}`, { method: 'DELETE' }))
}

// ---- checkout ----

export interface CheckoutResultData {
  orderId: string
  reference: string
  paymentIntentId: string
  status: string
}

export async function apiCheckout(
  paymentMethodId: string,
  idempotencyKey: string,
): Promise<CheckoutResultData> {
  return apiFetch<CheckoutResultData>('/checkout', {
    method: 'POST',
    body: { paymentMethodId },
    idempotencyKey,
  })
}

// ---- order detail (checkout settlement poll target) ----

export interface OrderLine {
  id: string
  kind: CartItemKind
  refId: string
  title: string
  subtitle?: string
  image?: string
  unitPrice: Money
  quantity: number
}

export interface OrderData {
  orderId: string
  reference: string
  status: string
  items: OrderLine[]
  subtotal: number
  fee: number
  total: number
  createdAt: string | null
}

interface OrderLineWire {
  id: string
  kind: string
  refId: string
  title: string
  subtitle: string | null
  image: string | null
  unitPrice: Money
  quantity: number
}

interface OrderSnapshotWire {
  items: OrderLineWire[]
  subtotal: Money
  fee: Money
  total: Money
  reference: string
  orderId: string
  status: string
  createdAt: string | null
}

function toOrderData(w: OrderSnapshotWire): OrderData {
  return {
    orderId: w.orderId,
    reference: w.reference,
    status: w.status,
    items: w.items.map((l) => ({
      id: l.id,
      kind: l.kind as CartItemKind,
      refId: l.refId,
      title: l.title,
      subtitle: l.subtitle ?? undefined,
      image: l.image ?? undefined,
      unitPrice: l.unitPrice,
      quantity: l.quantity,
    })),
    subtotal: w.subtotal.amount,
    fee: w.fee.amount,
    total: w.total.amount,
    createdAt: w.createdAt,
  }
}

export function orderQuery(orderId: string) {
  return queryOptions({
    queryKey: ['order', orderId],
    queryFn: async () => toOrderData(await apiFetch<OrderSnapshotWire>(`/me/orders/${orderId}`)),
  })
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx vitest run src/lib/api/queries/commerce.test.ts`
Expected: PASS (10/10).

- [ ] **Step 5: Commit**

```bash
git add Frontend/src/lib/api/queries/commerce.ts Frontend/src/lib/api/queries/commerce.test.ts
git commit -m "feat(frontend): commerce query layer — cart/checkout/order wire mapping"
```

---

## Task B2: `cart-context.tsx` — dual-mode `CartProvider` (guest local, authed server-backed, merge-on-login)

**Files:**
- Modify: `Frontend/src/features/cart/cart-context.tsx` (full rewrite)
- Test: `Frontend/src/features/cart/cart-context.test.tsx` (new — logic-level tests via a test harness, not full RTL render, matching the file's lack of an existing test file today; a lightweight approach is used since `CartProvider` has heavy DOM/query dependencies)

**Interfaces:**
- Consumes: `CART_KEY`, `CartItem`, `CartData`, `EMPTY_CART`, `cartQuery()`, `parseLineId`, `apiAddCartItem`, `apiUpdateCartItemQty`, `apiRemoveCartItem`, `apiCheckout`, `CheckoutResultData` (Task B1); `useAuth().isAuthenticated` (`Frontend/src/features/auth/auth-context.tsx`, already exists); `useToast()` (`Frontend/src/components/ui/toast-provider.tsx`, already exists); `ApiError` (`Frontend/src/lib/api/errors.ts`, already exists).
- Produces: `useCart(): CartContextValue` — **public shape preserved**: `items: CartItem[]`, `count: number`, `subtotal: number`, `fee: number`, `total: number`, `addItem: (item: Omit<CartItem, 'quantity'> & { quantity?: number }) => void`, `removeItem: (id: string) => void`, `setQuantity: (id: string, quantity: number) => void`, `clear: () => void`. **New**: `checkout: (paymentMethodId: string, idempotencyKey: string) => Promise<CheckoutResultData>` replaces the old synchronous `checkout: () => string`; `lastOrder`/`OrderSnapshot` are **removed** (Task B4 reads order state from `orderQuery`, not from the cart context). Task B3 (`checkout.index.tsx`) and Task B4 (`checkout.complete.tsx`) consume this.

- [ ] **Step 1: Write the failing test**

Create `Frontend/src/features/cart/cart-context.test.tsx`. This test exercises the provider via `renderHook` from `@testing-library/react` (already a project dependency — used elsewhere for hook-level tests; if not present, check `package.json` devDependencies for `@testing-library/react` before writing this file, since it must already be installed for this pattern to compile):

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { apiFetch } from '../../lib/api/client'
import { useCart, CartProvider } from './cart-context'

vi.mock('../../lib/api/client', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api/client')>('../../lib/api/client')
  return { ...actual, apiFetch: vi.fn() }
})

const mockUseAuth = vi.fn()
vi.mock('../auth/auth-context', () => ({ useAuth: () => mockUseAuth() }))

vi.mock('../../components/ui/toast-provider', () => ({ useToast: () => ({ toast: vi.fn() }) }))

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>
    <CartProvider>{children}</CartProvider>
  </QueryClientProvider>
}

beforeEach(() => {
  vi.mocked(apiFetch).mockReset()
  localStorage.clear()
  mockUseAuth.mockReturnValue({ isAuthenticated: false })
})

describe('CartProvider guest mode', () => {
  it('addItem/removeItem/setQuantity work locally with no API calls', () => {
    const { result } = renderHook(() => useCart(), { wrapper })

    act(() => result.current.addItem({
      id: 'track:t1', kind: 'track', title: 'Song', image: 'img.jpg',
      price: { amount: 5, currency: 'GHS' },
    }))

    expect(result.current.items).toHaveLength(1)
    expect(result.current.subtotal).toBe(5)
    expect(apiFetch).not.toHaveBeenCalled()

    act(() => result.current.removeItem('track:t1'))
    expect(result.current.items).toHaveLength(0)
  })
})

describe('CartProvider authed mode', () => {
  beforeEach(() => mockUseAuth.mockReturnValue({ isAuthenticated: true }))

  it('reads the cart from the server', async () => {
    vi.mocked(apiFetch).mockResolvedValue({
      items: [{
        id: 'track:t1', kind: 'track', refId: 't1', title: 'Song', subtitle: null, image: null,
        price: { amount: 5, currency: 'GHS' }, quantity: 1, stackable: false, metadata: null,
      }],
      subtotal: { amount: 5, currency: 'GHS' }, fee: { amount: 0.5, currency: 'GHS' },
      total: { amount: 5.5, currency: 'GHS' }, count: 1,
    })

    const { result } = renderHook(() => useCart(), { wrapper })

    await waitFor(() => expect(result.current.items).toHaveLength(1))
    expect(result.current.total).toBe(5.5)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx vitest run src/features/cart/cart-context.test.tsx`
Expected: FAIL — the current `cart-context.tsx` has no server-mode behavior; the authed-mode test fails (`items` stays `[]`, `apiFetch` never called since the current implementation never calls it).

- [ ] **Step 3: Write the implementation**

Replace `Frontend/src/features/cart/cart-context.tsx` entirely:

```tsx
/**
 * Cart store — dual mode. Logged out, it's the original localStorage-backed reducer (unchanged
 * behavior, no server calls). Logged in, it's backed by TanStack Query against /v1/me/cart, with
 * mutations (add/updateQty/remove) calling the real endpoints. On the logged-out→logged-in
 * transition, any local guest-cart lines are merged into the server cart and localStorage is
 * cleared. `checkout()` always calls the real POST /v1/checkout (there is no local-only checkout
 * path — a guest attempting it gets a real 401, surfaced by the caller).
 */

import { createContext, useContext, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/auth-context'
import { useToast } from '../../components/ui/toast-provider'
import { ApiError } from '../../lib/api/errors'
import {
  CART_KEY,
  EMPTY_CART,
  cartQuery,
  apiAddCartItem,
  apiUpdateCartItemQty,
  apiRemoveCartItem,
  apiCheckout,
  type CartData,
  type CartItem,
  type CheckoutResultData,
} from '../../lib/api/queries/commerce'

export type { CartItem, CartItemKind } from '../../lib/api/queries/commerce'

const PERSIST_KEY = 'beatzclik-cart'

/** Flat service fee (cedis) applied to a non-empty guest cart — mirrors the server's default fee. */
const GUEST_SERVICE_FEE = 0.5

interface GuestCartState {
  items: CartItem[]
}

type GuestAction =
  | { type: 'ADD'; item: CartItem }
  | { type: 'REMOVE'; id: string }
  | { type: 'SET_QTY'; id: string; quantity: number }
  | { type: 'CLEAR' }

function guestReducer(state: GuestCartState, action: GuestAction): GuestCartState {
  switch (action.type) {
    case 'ADD': {
      const existing = state.items.find((i) => i.id === action.item.id)
      if (existing) {
        if (!existing.stackable) return state
        return {
          items: state.items.map((i) =>
            i.id === action.item.id ? { ...i, quantity: i.quantity + action.item.quantity } : i,
          ),
        }
      }
      return { items: [...state.items, action.item] }
    }
    case 'REMOVE':
      return { items: state.items.filter((i) => i.id !== action.id) }
    case 'SET_QTY':
      return {
        items: state.items.map((i) =>
          i.id === action.id ? { ...i, quantity: Math.max(1, Math.min(99, action.quantity)) } : i,
        ),
      }
    case 'CLEAR':
      return { items: [] }
    default:
      return state
  }
}

function loadGuestCart(): GuestCartState {
  try {
    const raw = typeof window !== 'undefined' ? localStorage.getItem(PERSIST_KEY) : null
    if (!raw) return { items: [] }
    const parsed = JSON.parse(raw) as Partial<GuestCartState>
    return { items: parsed.items ?? [] }
  } catch {
    return { items: [] }
  }
}

interface CartContextValue {
  items: CartItem[]
  count: number
  subtotal: number
  fee: number
  total: number
  addItem: (item: Omit<CartItem, 'quantity'> & { quantity?: number }) => void
  removeItem: (id: string) => void
  setQuantity: (id: string, quantity: number) => void
  clear: () => void
  /** Always calls the real POST /v1/checkout — there is no local-only checkout path. */
  checkout: (paymentMethodId: string, idempotencyKey: string) => Promise<CheckoutResultData>
}

const CartContext = createContext<CartContextValue | null>(null)

export function CartProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  // ---- guest (local) state — always maintained so a login-time merge has something to read ----
  const [guestState, dispatchGuest] = useReducer(guestReducer, undefined, loadGuestCart)
  const guestFirstRender = useRef(true)
  useEffect(() => {
    if (guestFirstRender.current) { guestFirstRender.current = false; return }
    try { localStorage.setItem(PERSIST_KEY, JSON.stringify(guestState)) } catch { /* ignore */ }
  }, [guestState])

  // ---- server (authed) state ----
  const { data: serverCart } = useQuery({ ...cartQuery(), enabled: isAuthenticated })

  // ---- merge-on-login: POST each guest line into the server cart, then clear local storage ----
  const wasAuthed = useRef(isAuthenticated)
  useEffect(() => {
    const justLoggedIn = !wasAuthed.current && isAuthenticated
    wasAuthed.current = isAuthenticated
    if (!justLoggedIn || guestState.items.length === 0) return

    const itemsToMerge = guestState.items
    void (async () => {
      for (const item of itemsToMerge) {
        try {
          await apiAddCartItem(item)
        } catch (e) {
          if (!(e instanceof ApiError && e.status === 409)) {
            toast(`Could not move "${item.title}" to your cart`, 'error')
          }
        }
      }
      dispatchGuest({ type: 'CLEAR' })
      try { localStorage.removeItem(PERSIST_KEY) } catch { /* ignore */ }
      queryClient.invalidateQueries({ queryKey: CART_KEY })
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated])

  const guestSubtotal = useMemo(
    () => guestState.items.reduce((sum, i) => sum + i.price.amount * i.quantity, 0),
    [guestState.items],
  )
  const guestFee = guestState.items.length > 0 ? GUEST_SERVICE_FEE : 0
  const guestCount = useMemo(
    () => guestState.items.reduce((sum, i) => sum + i.quantity, 0),
    [guestState.items],
  )
  const guestData: CartData = {
    items: guestState.items,
    subtotal: guestSubtotal,
    fee: guestFee,
    total: guestSubtotal + guestFee,
    count: guestCount,
  }

  const data: CartData = isAuthenticated ? (serverCart ?? EMPTY_CART) : guestData

  // ---- server mutations ----
  const addMutation = useMutation({
    mutationFn: apiAddCartItem,
    onSuccess: (next) => queryClient.setQueryData<CartData>(CART_KEY, next),
    onError: () => toast('Could not add item to cart', 'error'),
  })

  const updateQtyMutation = useMutation({
    mutationFn: ({ lineId, qty }: { lineId: string; qty: number }) => apiUpdateCartItemQty(lineId, qty),
    onMutate: async ({ lineId, qty }) => {
      const prev = queryClient.getQueryData<CartData>(CART_KEY)
      if (prev) {
        queryClient.setQueryData<CartData>(CART_KEY, {
          ...prev,
          items: prev.items.map((i) => (i.id === lineId ? { ...i, quantity: qty } : i)),
        })
      }
      return { prev }
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(CART_KEY, ctx.prev)
      toast('Could not update quantity', 'error')
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: CART_KEY }),
  })

  const removeMutation = useMutation({
    mutationFn: apiRemoveCartItem,
    onSuccess: (next) => queryClient.setQueryData<CartData>(CART_KEY, next),
    onError: () => toast('Could not remove item from cart', 'error'),
  })

  const checkoutMutation = useMutation({
    mutationFn: ({ paymentMethodId, idempotencyKey }: { paymentMethodId: string; idempotencyKey: string }) =>
      apiCheckout(paymentMethodId, idempotencyKey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CART_KEY }),
  })

  const value = useMemo<CartContextValue>(
    () => ({
      items: data.items,
      count: data.count,
      subtotal: data.subtotal,
      fee: data.fee,
      total: data.total,
      addItem: (item) => {
        const full: CartItem = { quantity: 1, ...item }
        if (isAuthenticated) {
          addMutation.mutate(full)
        } else {
          dispatchGuest({ type: 'ADD', item: full })
        }
      },
      removeItem: (id) => {
        if (isAuthenticated) {
          removeMutation.mutate(id)
        } else {
          dispatchGuest({ type: 'REMOVE', id })
        }
      },
      setQuantity: (id, quantity) => {
        if (isAuthenticated) {
          updateQtyMutation.mutate({ lineId: id, qty: quantity })
        } else {
          dispatchGuest({ type: 'SET_QTY', id, quantity })
        }
      },
      clear: () => dispatchGuest({ type: 'CLEAR' }),
      checkout: (paymentMethodId, idempotencyKey) =>
        checkoutMutation.mutateAsync({ paymentMethodId, idempotencyKey }),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data, isAuthenticated],
  )

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useCart(): CartContextValue {
  const ctx = useContext(CartContext)
  if (!ctx) throw new Error('useCart must be used within a <CartProvider>')
  return ctx
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx vitest run src/features/cart/cart-context.test.tsx`
Expected: PASS (2/2). If `@testing-library/react` is not already a devDependency, install it first: `npm install -D @testing-library/react` (check `Frontend/package.json` before running this — it is very likely already present given the project uses Vitest + React 19; only add it if genuinely missing).

- [ ] **Step 5: Type-check the whole frontend**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx tsc --noEmit`
Expected: no errors. This catches any of the 11 `addItem` call sites, `cart.tsx`, or `use-store-cart.ts` that might rely on a type no longer exported the same way. If errors appear, they will point at the exact file/line to fix (e.g. an import of `CartItemKind` or `CartItem` from `cart-context` — both are still re-exported by the `export type { ... }` line above, so this should be clean).

- [ ] **Step 6: Commit**

```bash
git add Frontend/src/features/cart/cart-context.tsx Frontend/src/features/cart/cart-context.test.tsx
git commit -m "feat(frontend): dual-mode CartProvider — server-backed when signed in, merge-on-login"
```

---

## Task B3: `checkout.index.tsx` — async submit, idempotency, provider mapping, error handling

**Files:**
- Modify: `Frontend/src/routes/checkout.index.tsx`

**Interfaces:**
- Consumes: `useCart().checkout(paymentMethodId, idempotencyKey): Promise<CheckoutResultData>` (Task B2); `ApiError` (`Frontend/src/lib/api/errors.ts`); `useToast()`.
- Produces: navigates to `/checkout/complete?orderId=<id>` on success — Task B4 reads this search param.

- [ ] **Step 1: Confirm the route's search-param type is unconstrained today**

Run: `grep -n "validateSearch" "Frontend/src/routes/checkout.complete.tsx"` — expected: no output (the route currently declares no search-param validation, so adding one in Task B4 is additive and this task does not need to touch route typing itself; `navigate({ to: '/checkout/complete', search: { orderId } })` will type-check once Task B4 adds `validateSearch`, so **do this task's `navigate` call using the plain object literal now** — TypeScript will catch any mismatch when Task B4 lands, since both tasks are in the same PR/branch).

- [ ] **Step 2: Rewrite `handlePay` and the payment-method picker mapping**

In `Frontend/src/routes/checkout.index.tsx`, make these changes:

Replace the imports at the top of the file:

```tsx
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { CreditCard } from 'lucide-react'
import { useRef, useState } from 'react'
import { cn } from '../utils/cn'
import { useCart } from '../features/cart/cart-context'
import { useToast } from '../components/ui/toast-provider'
import { ApiError } from '../lib/api/errors'
import { formatPrice } from '../lib/format'
```

Replace the `PAYMENT_METHODS` constant's `id` values to match backend provider wire values directly where possible, keeping `airtel` as the UI-facing id (unchanged UI copy) but mapping it at submit time — add this mapping helper right after the `PAYMENT_METHODS` array:

```tsx
/** UI picker id -> backend Provider wire value (airtel -> airteltigo; others pass through). */
function toProviderWireValue(pickerId: string): string {
  return pickerId === 'airtel' ? 'airteltigo' : pickerId
}
```

Replace the component body's state/hooks and `handlePay`:

```tsx
function CheckoutComponent() {
  const [selectedMethod, setSelectedMethod] = useState('mtn')
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()
  const { toast } = useToast()
  const { items, subtotal, fee, total, checkout } = useCart()
  const idempotencyKeyRef = useRef<string | null>(null)

  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Nothing to check out</h1>
        <p className="text-gray-500 dark:text-gray-300">Your cart is empty.</p>
        <Link to="/store" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Browse the store</Link>
      </div>
    )
  }

  const handlePay = async () => {
    if (submitting) return
    setSubmitting(true)
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current = crypto.randomUUID()
    }
    try {
      const result = await checkout(toProviderWireValue(selectedMethod), idempotencyKeyRef.current)
      navigate({ to: '/checkout/complete', search: { orderId: result.orderId } })
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        toast('Please log in to check out', 'error')
      } else if (e instanceof ApiError && e.status === 429) {
        toast('Too many checkout attempts — please wait a moment and try again', 'error')
      } else if (e instanceof ApiError && e.code === 'CHECKOUT_KIND_UNSUPPORTED') {
        toast("Some items in your cart can't be checked out yet — remove them to continue", 'error')
      } else {
        toast('Payment could not be started — please try again', 'error')
      }
    } finally {
      setSubmitting(false)
    }
  }
```

Update the "Pay" button to reflect submission state (find the existing `<button onClick={handlePay} ...>` and replace only its `onClick`/`disabled`/label, leaving all classNames unchanged):

```tsx
          <button
            onClick={handlePay}
            disabled={submitting}
            className="w-full h-14 rounded-full bg-beatz-green text-black font-bold text-lg flex items-center justify-center hover:scale-[1.02] active:scale-[0.98] transition-all shadow-lg shadow-beatz-green/20 disabled:opacity-60"
          >
            {submitting ? 'Processing…' : `Pay ${formatPrice({ amount: total, currency: 'GHS' })} with ${selectedMethod === 'card' ? 'card' : 'MoMo'}`}
          </button>
```

Leave every other part of the file (the payment-method list rendering, the MTN confirmation copy block, the order-review panel) exactly as-is — only the imports, `handlePay`, the new `toProviderWireValue` helper, `submitting` state, and the Pay button's `onClick`/`disabled`/label change.

- [ ] **Step 3: Type-check**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx tsc --noEmit`
Expected: no errors (this will also confirm `ApiError.code` exists — it does, per `Frontend/src/lib/api/errors.ts`).

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/routes/checkout.index.tsx
git commit -m "feat(frontend): async checkout submit with idempotency key + error handling"
```

---

## Task B4: `checkout.complete.tsx` — poll the order until settlement

**Files:**
- Modify: `Frontend/src/routes/checkout.complete.tsx` (full rewrite)

**Interfaces:**
- Consumes: `orderQuery(orderId)` (Task B1); navigation search param `orderId` from Task B3.
- Produces: nothing further consumed — this is the terminal screen of the flow.

- [ ] **Step 1: Rewrite the route**

Replace `Frontend/src/routes/checkout.complete.tsx` entirely:

```tsx
import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { Check, Download, Loader2, XCircle } from 'lucide-react'
import { orderQuery } from '../lib/api/queries/commerce'
import { formatPrice } from '../lib/format'

interface CheckoutCompleteSearch {
  orderId?: string
}

export const Route = createFileRoute('/checkout/complete')({
  validateSearch: (search: Record<string, unknown>): CheckoutCompleteSearch => ({
    orderId: typeof search.orderId === 'string' ? search.orderId : undefined,
  }),
  component: CheckoutCompleteComponent,
})

function CheckoutCompleteComponent() {
  const { orderId } = Route.useSearch()

  const { data: order, isLoading } = useQuery({
    ...orderQuery(orderId ?? ''),
    enabled: !!orderId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'pending' ? 2000 : false
    },
  })

  if (!orderId) {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <h1 className="text-title text-beatz-dark-bg dark:text-white">No recent order</h1>
        <Link to="/" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to home</Link>
      </div>
    )
  }

  if (isLoading || !order) {
    return <AuthorizingState />
  }

  if (order.status === 'pending') {
    return <AuthorizingState />
  }

  if (order.status === 'failed') {
    return (
      <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
        <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center">
          <XCircle className="text-red-500" size={32} />
        </div>
        <h1 className="text-title text-beatz-dark-bg dark:text-white">Payment failed</h1>
        <p className="text-gray-500 dark:text-gray-300 max-w-sm">
          Your payment could not be completed. Nothing was charged for this attempt.
        </p>
        <Link to="/cart" className="h-11 px-6 rounded-full bg-beatz-green text-black font-bold flex items-center">Back to cart</Link>
      </div>
    )
  }

  const itemCount = order.items.reduce((sum, i) => sum + i.quantity, 0)

  return (
    <div className="flex flex-col items-center gap-8 py-12 max-w-2xl mx-auto">
      {/* Success card */}
      <div className="w-full bg-white dark:bg-beatz-dark-surface-2 border border-gray-100 dark:border-transparent rounded-[2rem] p-12 flex flex-col items-center text-center gap-8 shadow-2xl">
        <div className="w-20 h-20 rounded-full bg-beatz-green/10 flex items-center justify-center">
          <div className="w-12 h-12 rounded-full bg-beatz-green flex items-center justify-center">
            <Check size={32} className="text-black" strokeWidth={3} />
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-4xl font-bold text-beatz-dark-bg dark:text-white tracking-tight">Payment confirmed</h1>
          <p className="text-gray-500 dark:text-gray-300 font-medium">
            {itemCount} item{itemCount > 1 ? 's' : ''} added to your library — yours forever.
          </p>
        </div>

        <div className="flex items-center gap-4 w-full justify-center">
          <Link to="/library" className="h-12 px-8 rounded-full bg-beatz-green text-black font-bold flex items-center justify-center hover:scale-105 transition-transform">
            Go to library
          </Link>
          <button className="h-12 px-8 rounded-full border border-gray-300 dark:border-white/20 text-beatz-dark-bg dark:text-white font-bold flex items-center justify-center gap-2 hover:bg-gray-100 dark:hover:bg-white/10 transition-colors">
            <Download size={18} /> Download all
          </button>
        </div>
      </div>

      {/* Receipt */}
      <div className="w-full flex flex-col gap-6 px-4">
        <div className="flex justify-between items-end border-b border-gray-100 dark:border-white/5 pb-4">
          <div className="flex flex-col gap-1">
            <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Receipt</span>
            <span className="font-mono text-beatz-dark-bg dark:text-white font-bold">{order.reference}</span>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className="text-[10px] font-bold text-gray-500 dark:text-gray-300 uppercase tracking-widest">Total paid</span>
            <span className="font-mono text-beatz-green font-bold">{formatPrice({ amount: order.total, currency: 'GHS' })}</span>
          </div>
        </div>

        <div className="flex flex-col gap-4">
          {order.items.map((item) => (
            <div key={item.id} className="flex items-center gap-4">
              <div className="w-12 h-12 rounded overflow-hidden shrink-0">
                <img src={item.image ?? ''} alt={item.title} className="w-full h-full object-cover" />
              </div>
              <div className="flex flex-col flex-1 min-w-0">
                <span className="font-bold text-beatz-dark-bg dark:text-white truncate">{item.title}</span>
                <span className="text-xs text-gray-500 dark:text-gray-300 truncate">{item.quantity > 1 ? `${item.quantity} × ` : ''}{item.subtitle}</span>
              </div>
              <span className="font-mono font-bold text-beatz-dark-bg dark:text-white">{formatPrice({ amount: item.unitPrice.amount * item.quantity, currency: 'GHS' })}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function AuthorizingState() {
  return (
    <div className="flex flex-col items-center justify-center text-center gap-4 py-32">
      <Loader2 className="animate-spin text-beatz-green" size={40} />
      <h1 className="text-title text-beatz-dark-bg dark:text-white">Authorizing on your phone…</h1>
      <p className="text-gray-500 dark:text-gray-300 max-w-sm">
        Approve the MoMo PIN prompt on your phone to complete this payment.
      </p>
    </div>
  )
}
```

Note: `paid`/`fulfilled` are handled implicitly — the component checks `pending` and `failed` explicitly and falls through to the receipt for everything else, so no separate "is this settled" set is needed.

- [ ] **Step 2: Type-check**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Run the full test suite**

Run: `export PATH="/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH" && cd "Frontend" && npx vitest run`
Expected: PASS, no regressions in any existing test file.

- [ ] **Step 4: Commit**

```bash
git add Frontend/src/routes/checkout.complete.tsx
git commit -m "feat(frontend): poll order settlement on the checkout receipt page"
```

---

## Task B5: Live QA + verify + open PR

- [ ] **Step 1: Start the stack and the frontend dev server**

Confirm the backend (`beatzmedia`) is running (`cd backend && ./mvnw quarkus:dev`, or already running per the user) and the Vite dev server is up with the proxy pointed at `http://127.0.0.1:8080` (per Global Constraints). Use the Browser tool to drive the app at its dev URL.

- [ ] **Step 2: Guest cart QA**

While logged out: add a track to cart from a track/album page, confirm it appears on `/cart` with correct title/price/image, confirm no network request was made to `/v1/me/cart*` (guest mode is purely local).

- [ ] **Step 3: Login-merge QA**

Log in with the guest cart non-empty. Confirm the cart page still shows the same item(s) after login (now server-backed), and confirm (via the browser's network tab) a `POST /v1/me/cart/items` fired during the transition and `localStorage['beatzclik-cart']` is now cleared.

- [ ] **Step 4: Checkout QA (MoMo)**

From `/cart`, click Checkout, select MTN MoMo, click Pay. Confirm navigation to `/checkout/complete?orderId=...` and the "Authorizing on your phone…" state renders first. Confirm it transitions to "Payment confirmed" once the sandbox gateway settles (should be within a few seconds), and the receipt shows the correct reference, total, and line images/subtitles (proving Task A2's `order_line.subtitle`/`.image` flowed through end-to-end).

- [ ] **Step 5: Ownership QA**

After settlement, navigate to `/library` and confirm the purchased track now shows as owned (via `GET /v1/me/collection`, invalidated by the settled checkout) — do a full page reload to prove this is server state, not local optimism, matching how prior slices proved persistence.

- [ ] **Step 6: Error-path QA**

Add a mix of a track and (if reachable via a store/podcast/event page) an episode/ticket/store item to the cart, then attempt checkout — confirm the `CHECKOUT_KIND_UNSUPPORTED` toast appears instead of a crash or generic error, per Task B3.

- [ ] **Step 7: Ask the user to run the verification gate**

Tell the user: "Frontend commerce wiring is implemented, QA'd, and committed on `feat/frontend-commerce-checkout`. Please run the frontend test suite and typecheck (`export PATH=\"/Users/mac/.nvm/versions/node/v22.17.1/bin:$PATH\" && cd Frontend && npx vitest run && npx tsc --noEmit`) if you'd like a second confirmation — I already ran both during implementation." (Frontend commands are not subject to the same JPS-race constraint as the backend `verify.sh`, so the agent may run these itself; only the backend gate is user-run per Global Constraints.)

- [ ] **Step 8: Open the PR**

```bash
gh pr create --base master --head feat/frontend-commerce-checkout \
  --title "feat(frontend): wire cart/checkout/orders to the real backend (slice 3)" \
  --body "$(cat <<'EOF'
## Summary
- `CartProvider` runs dual-mode: local/localStorage for guests (unchanged behavior), TanStack Query-backed against `/v1/me/cart` when signed in, merging guest-cart lines into the server cart on login.
- Checkout is now a real, idempotent `POST /v1/checkout` submit — the receipt page polls the new `GET /v1/me/orders/{orderId}` (WU-COM-3) until settlement instead of asserting success client-side, since MoMo settlement is asynchronous.
- Handles the real backend error paths honestly: 401 (please log in), 429 (rate limited, with the message from `Retry-After`), and `409 CHECKOUT_KIND_UNSUPPORTED` (some cart kinds aren't checkout-ready yet — a real, documented backend gate, not a bug).
- Depends on #<WU-COM-3 PR number> (merged).

## Test plan
- [x] Unit: `commerce.test.ts` (10), `cart-context.test.tsx` (2)
- [x] Full suite + typecheck clean (`npx vitest run && npx tsc --noEmit`)
- [x] Live QA: guest cart → login merge → MoMo checkout → pending→paid transition → receipt with images → library shows owned track after reload → `CHECKOUT_KIND_UNSUPPORTED` toast on a mixed-kind cart

## Spec / plan
- docs/superpowers/specs/2026-07-15-commerce-checkout-wiring-design.md
- docs/superpowers/plans/2026-07-15-commerce-checkout-wiring.md
EOF
)"
```

- [ ] **Step 9: Watch CI and report back to the user**

Report the result once CI settles, mirroring how slices 1/2a/2b were reported. Do not begin any further slice without the user's explicit go-ahead.
