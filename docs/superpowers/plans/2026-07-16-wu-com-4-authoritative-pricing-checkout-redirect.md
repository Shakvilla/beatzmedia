# WU-COM-4 — Authoritative pricing (episode/season-pass/ticket/store) + card hosted-checkout redirect

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal (Scope B — chosen by the user):** Replace the client-supplied-metadata pricing echo for `episode`/`season_pass`/`ticket`/`store` cart kinds with authoritative, server-resolved prices sourced from the owning modules; surface the WU-PAY-6 card hosted-checkout `checkoutUrl` out through the `/v1/checkout` response; AND wire settlement→fulfillment + the 70/30 revenue split for all four kinds, then un-gate checkout so they are end-to-end purchasable. Money-critical; security-reviewer sign-off REQUIRED.

**Architecture:** Commerce declares a new outbound SPI `ModulePriceSource` (in `commerce/application/port/out`); the podcasts, events, and store modules each implement it (a `module → commerce` edge, identical in direction to the pre-existing `podcasts → commerce` ownership edge and the `store → commerce` event-subscriber edge — so **no dependency cycle**, mirroring the WU-SRCH-2 `IndexSource` SPI pattern). `CatalogPricingServiceAdapter` stops echoing `metadata.priceMinor` for the four external kinds and instead dispatches to the matching `ModulePriceSource` by `entityType`. Separately, a nullable `checkoutUrl` is threaded across the `payments → commerce` charge seam (`ChargeResult` → `CheckoutResult`) and persisted on the `Order` so idempotent replays return it too.

**Tech Stack:** Java 25, Quarkus 3.37.x CDI, JPA/Hibernate + PostgreSQL 16, Flyway forward-only migrations, JUnit 5 (`org.junit.jupiter.api.Assertions` — **AssertJ is NOT a dependency**), Testcontainers for ITs, ArchUnit for the dependency rule.

## Global Constraints

- **Hexagonal dependency rule:** `adapter → application → domain`; domain imports no framework. Modules never read another module's tables — call its input port, react to its domain event, or implement its declared SPI. The SPI approach here keeps every new edge pointing **into** commerce (`module → commerce`), never out of it. Verify with ArchUnit.
- **Money** is integer minor units (pesewas); API uses `{ amount, currency: "GHS" }`. Never trust a client-supplied price (INV-11). Prices resolve from the owning module's own persisted data.
- **Un-gating is the goal, but it is the LAST step (Task 13).** The checkout gate (`CheckoutService.gateKind` → 409, ADR-23) stays in force until every un-gated kind has: authoritative pricing (Tasks 1–4), a resolvable payee, ownership/fulfillment on settlement, and its 70/30 split posting (Tasks 8–12). Un-gate a kind ONLY after its full settlement path is wired and tested — never before. A kind whose payee cannot be resolved for a specific item must fail at pricing time (Task 8 guard), so an un-gated charge can never settle into unaccounted funds.
- **INV-1 / INV-4 / INV-6 are non-negotiable:** ownership only on settlement; every settled line's gross is attributed (70/30 to the resolved creator, or fully to platform revenue with an audit if the payee is legitimately absent — never silently dropped); the ledger stays balanced. INV-10: every privileged settlement mutation appends an AuditEntry. Idempotency/exactly-once on settlement is already enforced by the `order_grant_posting` per-order claim + `ledger.claimPosting(refType,refId)` + `IssueTicketService`'s own `ticketExistsForOrderTier` replay — new fulfillment must ride these, not add a parallel un-guarded path.
- **`checkoutUrl` is null in every environment until go-live.** It is non-null only for a `card` charge when `PSP_REDDE` is enabled (Redde active). `PSP_REDDE` is seeded `false` (V966) and stays off until real Redde credentials (deploy-secret human gate) land. All new `checkoutUrl` code must be correct with the value null (the default path) and correct when non-null (Redde+card).
- **Definition of Done** (per `backend/docs/01-conventions-and-standards.md` §11): unit + integration + contract tests pass; ArchUnit green; Flyway migration applies on an empty DB; `docker compose up` boots healthy; coverage gate met; Spotless clean; commerce ADD + payments ADD updated in the same PR; ADR recorded.
- **Branch:** `feat/WU-COM-4-authoritative-pricing`. Commits: `feat(commerce): WU-COM-4 <slug>`. One PR using the template. Security-reviewer sign-off is REQUIRED (money path; carryover G3 from WU-COM-2).
- **Migration version:** allocate with `bash backend/scripts/next-migration-version.sh` at implementation time. As of planning it is **969** (`V969__commerce_order_checkout_url.sql`); re-run the script — other work may land first.

---

## File structure

**New (Part A — pricing SPI):**
- `commerce/application/port/out/ModulePriceSource.java` — the SPI: `String entityType()` + `PricedItem price(String refId, Map<String,Object> metadata)`.
- `podcasts/adapter/out/pricing/EpisodePriceSource.java`, `SeasonPassPriceSource.java` — implement the SPI (`entityType()` = `"episode"` / `"season-pass"`).
- `events/adapter/out/pricing/TicketPriceSource.java` — `entityType()` = `"ticket"`.
- `store/adapter/out/pricing/StorePriceSource.java` — `entityType()` = `"store"`.
- `podcasts/application/port/in/GetEpisode.java` + `podcasts/application/service/GetEpisodeService.java` — a by-`EpisodeId` price/detail lookup (podcasts has none today; `ListEpisodes` returns a whole show). Backed by existing out port `PodcastRepository.findEpisode(EpisodeId)`.
- `events/application/port/in/GetTicketTierPrice.java` + `events/application/service/GetTicketTierPriceService.java` — tier price+availability by `(EventId, TicketTierId)`; existing `TicketTierView` deliberately hides tier id + counters, so a new port is unavoidable. Backed by `EventRepository.findById(EventId)`.

**Modified (Part A):**
- `commerce/adapter/out/persistence/CatalogPricingServiceAdapter.java` — inject `Instance<ModulePriceSource>` (CDI discovery, same as `ReindexService`'s `Instance<IndexSource>`); for `episode/season_pass/ticket/store` dispatch to the source whose `entityType()` matches `kind.wireValue()`; delete `priceFromMetadata` + `toPositiveLong`. `track/album/album_rest` unchanged.

**Modified (Part B — checkoutUrl seam):**
- `commerce/application/port/out/ChargeGateway.java` — add `checkoutUrl` to `ChargeResult`.
- `commerce/adapter/out/integration/PaymentsChargeGatewayAdapter.java` — map `view.checkoutUrl()` into `ChargeResult` (line ~61).
- `commerce/application/port/in/CheckoutResult.java` — add nullable `checkoutUrl` component (auto-serialized by `CheckoutResource`).
- `commerce/domain/Order.java` + `commerce/adapter/out/persistence/OrderEntity.java` — nullable `checkoutUrl` field/column so idempotent replay (`toResult`) returns it.
- `commerce/application/service/CheckoutService.java` — set `checkoutUrl` from `ChargeResult` on the fresh path; persist on the `Order`; `toResult` reads it.
- `backend/src/main/resources/db/migration/V969__commerce_order_checkout_url.sql` — `ALTER TABLE order_record ADD COLUMN checkout_url TEXT;` (confirm the exact orders table name at implementation time — see Task 5).

**Docs:**
- `API-CONTRACT.md` — document `checkoutUrl?: string | null` on the `/checkout` response.
- `backend/docs/00-system-architecture.md` §9 — new ADR (SPI-for-pricing + checkoutUrl seam).
- `backend/docs/architecture/commerce.md`, `payments.md` — as-built notes.
- `backend/.project/backlog.yaml` — flip `WU-COM-4` `todo → in_progress` at start, `→ in_review` at PR.

---

## Task 1: `ModulePriceSource` SPI + commerce dispatch (no behavior change yet)

**Files:**
- Create: `commerce/application/port/out/ModulePriceSource.java`
- Modify: `commerce/adapter/out/persistence/CatalogPricingServiceAdapter.java`
- Test: `commerce/adapter/out/persistence/CatalogPricingServiceAdapterTest.java` (new)

**Interfaces:**
- Produces: `interface ModulePriceSource { String entityType(); PricedItem price(String refId, Map<String,Object> metadata); }` — `entityType()` returns a `CartItemKind.wireValue()` (`"episode"`, `"season-pass"`, `"ticket"`, `"store"`). `PricedItem` is the existing `commerce/application/port/out/PricedItem` record `(String title, String subtitle, String image, Money unitPrice)`.
- Consumes: existing `PricingService` out port + `PriceUnavailableException`.

- [ ] **Step 1: Write the SPI interface**

```java
package org.shakvilla.beatzmedia.commerce.application.port.out;

import java.util.Map;

/**
 * Outbound SPI: an owning module (podcasts/events/store) supplies the authoritative price for one
 * cart {@code entityType}. Declared by commerce and implemented by the owning module, so the only
 * new dependency edge is {@code module -> commerce} (same direction as the existing
 * {@code podcasts -> commerce} ownership edge) — no cycle. Mirrors the WU-SRCH-2 {@code IndexSource}
 * pattern. Implementations MUST resolve the price from the module's own persisted data and never
 * trust client-supplied {@code metadata} for the amount (INV-11); {@code metadata} may be consulted
 * only to select among the module's own priced options (e.g. store {@code licenseTier}).
 */
public interface ModulePriceSource {
  /** The {@code CartItemKind.wireValue()} this source prices, e.g. {@code "episode"}. */
  String entityType();

  /** Authoritative price + display fields for {@code refId}; throws PriceUnavailableException if not purchasable. */
  PricedItem price(String refId, Map<String, Object> metadata);
}
```

- [ ] **Step 2: Write the failing dispatch test**

Inject fake sources into the adapter and assert dispatch-by-entityType + unknown-kind failure. Uses JUnit 5 assertions only.

```java
package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.ModulePriceSource;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

class CatalogPricingServiceAdapterTest {

  private static ModulePriceSource source(String type, PricedItem out) {
    return new ModulePriceSource() {
      public String entityType() { return type; }
      public PricedItem price(String refId, Map<String, Object> md) {
        if (out == null) throw new PriceUnavailableException(type, refId);
        return out;
      }
    };
  }

  @Test
  void dispatchesEpisodeToItsSource() {
    PricedItem episode = new PricedItem("Ep 1", "Show", "img", Money.ofMinor(500, Currency.GHS));
    var adapter = new CatalogPricingServiceAdapter(
        null, // EntityManager unused for external kinds
        new TestInstance(List.of(source("episode", episode), source("ticket", null))));
    PricedItem result = adapter.priceFor(CartItemKind.episode, "ep-1", Map.of());
    assertEquals(episode, result);
  }

  @Test
  void unknownEntityTypeThrowsPriceUnavailable() {
    var adapter = new CatalogPricingServiceAdapter(null, new TestInstance(List.of()));
    assertThrows(PriceUnavailableException.class,
        () -> adapter.priceFor(CartItemKind.season_pass, "show-1", Map.of()));
  }
}
```

> Note: `TestInstance` is a tiny `jakarta.enterprise.inject.Instance<ModulePriceSource>` fake backing a `List` — write it as a nested test helper (implement `iterator()`/`stream()`; throw `UnsupportedOperationException` for the CDI-lifecycle methods you don't use, exactly as `ReindexServiceTest` fakes `Instance<IndexSource>`; copy that test's helper). Confirm the shape against `ReindexServiceTest` before writing.

- [ ] **Step 3: Rewire the adapter**

Constructor becomes `CatalogPricingServiceAdapter(EntityManager em, Instance<ModulePriceSource> priceSources)`. Build a lookup once: `Map<String, ModulePriceSource> byType` from the injected instance (fail fast on duplicate `entityType()`). Replace the `episode, season_pass, ticket, store` switch arm:

```java
case episode, season_pass, ticket, store -> {
  ModulePriceSource src = byType.get(kind.wireValue());
  if (src == null) {
    throw new PriceUnavailableException(kind.wireValue(), refId);
  }
  yield src.price(refId, metadata);
}
```

Delete `priceFromMetadata` and `toPositiveLong`. Keep `priceTrack`/`priceAlbum` verbatim.

- [ ] **Step 4: Run the tests**

Run: `cd backend && ./mvnw -q -pl . test -Dtest=CatalogPricingServiceAdapterTest` (user runs the real gate — see Verification). Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/shakvilla/beatzmedia/commerce/application/port/out/ModulePriceSource.java \
        backend/src/main/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/CatalogPricingServiceAdapter.java \
        backend/src/test/java/org/shakvilla/beatzmedia/commerce/adapter/out/persistence/CatalogPricingServiceAdapterTest.java
git commit -m "feat(commerce): WU-COM-4 ModulePriceSource SPI + dispatch"
```

> After Step 3 the four external kinds have **no** registered source yet, so they throw `PriceUnavailableException` on add-to-cart until Tasks 2–4 land. That is acceptable mid-branch (they were metadata-echoed before, and checkout gates them regardless); the branch is only shippable once all four sources exist. Do NOT open the PR before Task 4.

---

## Task 2: podcasts `GetEpisode` port + `EpisodePriceSource` + `SeasonPassPriceSource`

**Files:**
- Create: `podcasts/application/port/in/GetEpisode.java`, `podcasts/application/service/GetEpisodeService.java`
- Create: `podcasts/adapter/out/pricing/EpisodePriceSource.java`, `podcasts/adapter/out/pricing/SeasonPassPriceSource.java`
- Test: `podcasts/application/service/GetEpisodeServiceTest.java`, `podcasts/adapter/out/pricing/PodcastPriceSourceTest.java`

**Interfaces:**
- Consumes: `PodcastRepository.findEpisode(EpisodeId)` (existing out port; returns domain `PodcastEpisode` with `price():Optional<Money>`, `title()`, `image()`, `showTitle()`), `GetPodcast.get(PodcastId)` (existing; `PodcastView.seasonPassPrice():MoneyView`), commerce's `ModulePriceSource` + `PricedItem` + `PriceUnavailableException`.
- Produces: `GetEpisode.get(EpisodeId):EpisodePriceView` (record `(String id, String title, String showTitle, String image, MoneyView price, boolean purchasable)`).

**Decisions:**
- `episode` refId = `EpisodeId.value()`. Price = `podcast_episode.price_minor`. `purchasable` = premium/early-access AND price present. A non-premium/free episode has no price → `PriceUnavailableException("episode", refId)`.
- `season_pass` refId = `PodcastId.value()` (the show). Price = `podcast.season_pass_price_minor`; **nullable** (no season pass) → `PriceUnavailableException("season-pass", refId)`.
- Both sources map commerce's `Money` from the module's `MoneyView`/`Money` via `Money.ofMinor(...)` in `Currency.GHS`.

- [ ] **Step 1: Failing test — `GetEpisodeService` returns price for a premium episode, 404s an unknown id**

```java
// GetEpisodeServiceTest: given a FakePodcastRepository holding a premium episode with price_minor=500,
// get(new EpisodeId("ep-1")).price().amount() equals the ₵5.00 view; unknown id throws EpisodeNotFoundException.
```
Use the existing podcasts test fakes (grep `podcasts` test dir for `FakePodcastRepository`); assert with JUnit 5.

- [ ] **Step 2: Run — expect FAIL** (`GetEpisode` not defined). `./mvnw -q test -Dtest=GetEpisodeServiceTest`.

- [ ] **Step 3: Implement `GetEpisode` + `GetEpisodeService`**

```java
// GetEpisode.java (port/in)
public interface GetEpisode { EpisodePriceView get(EpisodeId episodeId); }
public record EpisodePriceView(String id, String title, String showTitle, String image,
    MoneyView price, boolean purchasable) {}
```
`GetEpisodeService` (`@ApplicationScoped`, injects `PodcastRepository`): `findEpisode(id).orElseThrow(() -> new EpisodeNotFoundException(id))`; build the view; `purchasable = episode.isGated() && episode.price().isPresent()`; `price = episode.price().map(m -> MoneyView.ofMinor(m.minor(), "GHS")).orElse(null)`. (Reuse the existing not-found exception if one exists; else add a minimal domain exception mapped to 404 — grep for `EpisodeNotFound`/`PodcastNotFound` first.)

- [ ] **Step 4: Run — expect PASS.**

- [ ] **Step 5: Implement the two `ModulePriceSource` beans + their test**

```java
// EpisodePriceSource.java (podcasts/adapter/out/pricing)
@ApplicationScoped
public class EpisodePriceSource implements ModulePriceSource {
  private final GetEpisode getEpisode;
  @Inject public EpisodePriceSource(GetEpisode g) { this.getEpisode = g; }
  public String entityType() { return "episode"; }
  public PricedItem price(String refId, Map<String,Object> md) {
    var v = getEpisode.get(new EpisodeId(refId));            // throws 404 if missing
    if (!v.purchasable() || v.price() == null) throw new PriceUnavailableException("episode", refId);
    return new PricedItem(v.title(), v.showTitle(), v.image(),
        Money.ofMinor(v.price().amount()... , Currency.GHS)); // convert MoneyView -> minor units
  }
}
```
> `MoneyView` carries `BigDecimal amount` — convert to minor units via the existing helper (grep `MoneyView` for a `toMinor()`/`ofMinor` round-trip; if none, multiply by 100 with `movePointRight(2)` and `longValueExact()`). Confirm at implementation time; do NOT hand-roll float math.

`SeasonPassPriceSource` (`entityType()="season-pass"`): `getPodcast.get(new PodcastId(refId))`; if `view.seasonPassPrice()==null` → `PriceUnavailableException("season-pass", refId)`; else `PricedItem(view.title(), view.publisher(), view.image(), <minor from seasonPassPrice>)`.

`PodcastPriceSourceTest`: fake `GetEpisode`/`GetPodcast`, assert each source maps price + throws when unpriced.

- [ ] **Step 6: Commit** — `feat(podcasts): WU-COM-4 GetEpisode port + episode/season-pass price sources`.

---

## Task 3: events `GetTicketTierPrice` port + `TicketPriceSource`

**Files:**
- Create: `events/application/port/in/GetTicketTierPrice.java`, `events/application/service/GetTicketTierPriceService.java`
- Create: `events/adapter/out/pricing/TicketPriceSource.java`
- Test: `events/application/service/GetTicketTierPriceServiceTest.java`, `events/adapter/out/pricing/TicketPriceSourceTest.java`

**Interfaces:**
- Consumes: `EventRepository.findById(EventId)` (existing; returns `Event` with full `List<TicketTier>` incl. `id()`, `priceMinor()`, `remaining()`, `isSoldOut()`, `name()`).
- Produces: `GetTicketTierPrice.get(EventId, TicketTierId):TicketTierPriceView` (record `(String tierId, String tierName, String eventTitle, String image, long priceMinor, boolean soldOut)`).

**Decisions — resolve the refId ambiguity first (OPEN QUESTION):**
- `ticket` refId is the composite `"eventId:tierId"` (split on the **first** colon; eventId won't contain one, tier segment might per the store precedent — but for tickets the domain is keyed by `TicketTierId`). **Confirm** whether the second segment is a `TicketTierId.value()` or a tier *name* (frontend example `evt-1:VIP` looks name-like). Grep `Frontend/src/features/events` + `IssueTicketCommand`/`IssueTicketService` for how they parse it, and match that exactly. If it is a name, look the tier up by `name` within the event; if an id, by `TicketTierId`. **The source and the (future) `IssueTicket` settlement path MUST parse it identically** — a mismatch would price one tier and fulfill another. Record the resolved convention in the ADR.
- Price = the resolved tier's `priceMinor()`. If the tier is not found → `PriceUnavailableException("ticket", refId)`.
- Availability: if `tier.isSoldOut()` → `PriceUnavailableException("ticket", refId)` (do not let a sold-out tier sit priced in the cart). Capacity is still enforced authoritatively at settlement by `IssueTicket` (out of scope here).

- [ ] **Step 1: Failing test** — `GetTicketTierPriceService` returns the VIP tier price for `(evt-1, <vip>)`, throws for an unknown tier and for a sold-out tier. Use existing events fakes (`FakeEventRepository`).
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement port + service** (`@ApplicationScoped`, injects `EventRepository`): load event, find tier by the resolved key, guard not-found + sold-out, map to view.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Implement `TicketPriceSource`** (`entityType()="ticket"`): parse `refId` → `(eventId, tierKey)`; call the port; `new PricedItem(eventTitle, tierName, image, Money.ofMinor(priceMinor, Currency.GHS))`. Test the parse + mapping incl. a malformed refId (no colon) → `PriceUnavailableException`.
- [ ] **Step 6: Commit** — `feat(events): WU-COM-4 ticket-tier price port + source`.

---

## Task 4: store `StorePriceSource` (reuses existing `GetStoreItem`)

**Files:**
- Create: `store/adapter/out/pricing/StorePriceSource.java`
- Test: `store/adapter/out/pricing/StorePriceSourceTest.java`

**Interfaces:**
- Consumes: `GetStoreItem.get(StoreItemId):StoreItemView` (existing; `price():MoneyView`, `licenseOptions():List<LicenseOptionView>` where `LicenseOptionView(tier,label,price,...)`, `title`, `artistName`, `image`). Unknown id → `StoreItemNotFoundException` (already 404).

**Decisions:**
- `store` refId = `StoreItemId.value()`, possibly suffixed `:<note>` (license tier or merch size). **Strip the note**: split on the first colon → `storeItemId`. Keep the raw note only if needed to disambiguate, but the authoritative tier selector is `metadata.licenseTier` (per `AddCartItemCommand` javadoc), NOT the refId note.
- Base price = `StoreItemView.price()`. For a `BEAT_LICENSE` item with `metadata.licenseTier` set, the price is the matching `LicenseOptionView.price()` (authoritative per-tier price from the module) — select by `tier`. If `licenseTier` is given but matches no option → `PriceUnavailableException("store", refId)` (never fall back to base for a spoofed tier). Merch/variants carry no per-variant price → base price stands.

- [ ] **Step 1: Failing test** — base price for a plain store item; the selected license-tier price for a `BEAT_LICENSE` item with `metadata.licenseTier="pro"`; `PriceUnavailableException` for an unknown licenseTier; refId note stripped correctly (`item-1:M` → looks up `item-1`). Fake `GetStoreItem`.
- [ ] **Step 2: Run — expect FAIL.**
- [ ] **Step 3: Implement `StorePriceSource`** (`entityType()="store"`, injects `GetStoreItem`): strip note; `var view = getStoreItem.get(new StoreItemId(id));` select price (base, or license option by `metadata.licenseTier`); return `PricedItem(view.title(), view.artistName(), view.image(), <minor>)`.
- [ ] **Step 4: Run — expect PASS.**
- [ ] **Step 5: Commit** — `feat(store): WU-COM-4 store price source`.

> At the end of Task 4 all four `ModulePriceSource` beans exist, so `CatalogPricingServiceAdapter` prices every kind authoritatively and the add-to-cart spoof vector is closed. Add a commerce IT (`AddToCartPricingIT`) that seeds a premium episode / season pass / ticket tier / store item and asserts the cart line's stored price equals the module's price and **ignores** a spoofed `metadata.priceMinor`. This is the security-critical regression — make it have teeth (assert the spoofed value is NOT used).

---

## Task 5: Thread `checkoutUrl` across the payments→commerce seam (+ persist on Order)

**Files:**
- Modify: `commerce/application/port/out/ChargeGateway.java`, `commerce/adapter/out/integration/PaymentsChargeGatewayAdapter.java`
- Modify: `commerce/application/port/in/CheckoutResult.java`
- Modify: `commerce/domain/Order.java`, `commerce/adapter/out/persistence/OrderEntity.java`, `commerce/application/service/CheckoutService.java`
- Create: `backend/src/main/resources/db/migration/V969__commerce_order_checkout_url.sql`
- Test: `commerce/application/CheckoutServiceTest.java` (extend), `commerce/it/CheckoutFlowIT.java` (extend)

**Interfaces:**
- `ChargeGateway.ChargeResult(String paymentIntentId, String status, String checkoutUrl)` — add nullable third component.
- `CheckoutResult(String orderId, String reference, String paymentIntentId, String status, String checkoutUrl)` — add nullable fifth component (serialized directly by `CheckoutResource`, so the wire gains `checkoutUrl`).

**Decisions:**
- `checkoutUrl` is nullable end-to-end and null on the sandbox/MoMo path (the only path until Redde go-live). No new branching in `CheckoutService` — just carry the value.
- Persist on `Order` (nullable `checkout_url` column) so the **idempotent replay** path (`toResult`, which rebuilds from the persisted `Order`, never from `ChargeResult`) returns the same redirect URL. Set it right after `attachPaymentIntent`.
- **Confirm the orders table name** before writing the migration: `grep -rn "@Table" commerce/adapter/out/persistence/OrderEntity.java` (this plan assumes `order_record`; the entity is authoritative).

- [ ] **Step 1: Failing unit test** — `CheckoutServiceTest`: a fake `ChargeGateway` returning `checkoutUrl="https://redde/checkout/x"` makes `checkout(...)` return a `CheckoutResult` whose `checkoutUrl()` equals it; a fake returning `null` yields `null`. Update the existing fake `ChargeGateway` in the test to the new 3-arg `ChargeResult`.
- [ ] **Step 2: Run — expect FAIL** (compile error: `ChargeResult`/`CheckoutResult` arity).
- [ ] **Step 3: Add the fields**
  - `ChargeResult` → 3 components; `PaymentsChargeGatewayAdapter` line ~61 → `new ChargeResult(view.id(), view.status(), view.checkoutUrl())`.
  - `CheckoutResult` → 5 components.
  - `Order`: add `private String checkoutUrl;` + `attachCheckoutUrl(String)` (or extend `attachPaymentIntent`) + getter; `OrderEntity`: `@Column("checkout_url") String checkoutUrl` + map in both directions.
  - `CheckoutService`: fresh path `new CheckoutResult(order.getId().value(), reference, charge.paymentIntentId(), order.getStatus().wireValue(), charge.checkoutUrl())`; persist `order.attachCheckoutUrl(charge.checkoutUrl())` before the final `save`; `toResult(Order)` → include `order.getCheckoutUrl()`.
- [ ] **Step 4: Write the migration**

```sql
-- V969__commerce_order_checkout_url.sql  (confirm table name against OrderEntity @Table)
ALTER TABLE order_record ADD COLUMN checkout_url TEXT;
```
- [ ] **Step 5: Run unit tests — expect PASS.**
- [ ] **Step 6: Extend `CheckoutFlowIT`** — assert the 202 body **contains** `checkoutUrl` and it is `null` on the default sandbox path (present-but-nullable contract). Keep the existing `orderId`/`reference`/`paymentIntentId`/`status` assertions.
- [ ] **Step 7: Commit** — `feat(commerce): WU-COM-4 surface checkoutUrl through checkout response`.

---

## Task 6: Contract test + ArchUnit dependency check

**Files:**
- Modify/create: the commerce contract test asserting the `/checkout` response shape (grep `src/test` for the existing checkout contract test; if only `CheckoutFlowIT` exists, add the field assertion there and in the contract-test module).
- Modify: the ArchUnit test module (grep `src/test` for `*ArchTest*` / `*Architecture*`).

- [ ] **Step 1:** Contract test asserts `checkoutUrl` is a present, nullable string on the checkout response and its absence does not break existing consumers (null serializes as JSON `null` or is omitted — match the project's Jackson config; assert whichever the resource produces).
- [ ] **Step 2:** ArchUnit: add/confirm a rule that `podcasts`, `events`, `store` may depend on `commerce.application.port.out.ModulePriceSource` (the SPI) but commerce must NOT depend on those modules — i.e. **no `commerce → {podcasts,events,store}` edge**. Run the full ArchUnit suite (it runs in-suite; `-Dgroups=arch` runs ZERO tests in this repo — do not use it). Expected: green, proving no cycle.
- [ ] **Step 3: Commit** — `test(commerce): WU-COM-4 contract + archunit for pricing SPI & checkoutUrl`.

---

## Task 8: `SettlementSource` SPI + checkout-time payee guard

**Files:**
- Create: `commerce/application/port/out/SettlementSource.java`, `commerce/application/port/out/SettlementContext.java`
- Modify: `commerce/adapter/out/persistence/CatalogPricingServiceAdapter.java` (payee-existence guard on the four kinds) OR add the guard in `CheckoutService`/`AddCartItemService` — decide in the task; the guard must run at pricing time so an un-payable item is unbuyable.
- Test: `commerce/application/service/SettlementSourceRegistryTest.java`

**Interfaces:**
- Produces:
  ```java
  public interface SettlementSource {
    String entityType();                                   // CartItemKind.wireValue()
    java.util.Optional<AccountId> payee(String refId);     // creator/artist/seller to credit; empty allowed only when Task 8 guard permits
    default java.util.List<String> ownedEpisodeIds(String refId) { return java.util.List.of(); } // episode/season-pass expand to episode ids commerce grants
    default void fulfill(SettlementContext ctx) {}         // ticket mint / store stock; no-op for episode/season (commerce writes the grants)
  }
  public record SettlementContext(String refId, OrderId orderId, AccountId buyer, int qty, String idempotencyKey) {}
  ```
  `AccountId` is `identity.domain.AccountId`; `OrderId` is `commerce.domain.OrderId`.
- Consumes: CDI `Instance<SettlementSource>` (same discovery pattern as `ModulePriceSource` / `ReindexService`).

**Decisions:**
- The SPI unifies the three settlement concerns per kind so the `GrantOwnershipService` loop stays a clean dispatch: **payee** (for the split), **ownedEpisodeIds** (commerce writes `forEpisode` grants for episode/season-pass), **fulfill** (owning-module side effect — ticket mint, store stock). Track/album/album-rest keep the existing `CatalogExpansionReader` path untouched.
- **Payee guard (closes the unaccounted-funds hole):** at pricing time (add-to-cart AND checkout re-price) for the four kinds, require `settlementSource.payee(refId)` to be present; if empty → `PriceUnavailableException(kind, refId)`. This guarantees an un-gated line can never settle without a creditable payee. (Rationale from research: `event.artist_id` / `podcast.creator_account_id` / `store_item.artist_id` are all nullable; a null must block the sale, not silently route funds nowhere.)

- [ ] Step 1: Failing test — a registry over `Instance<SettlementSource>` maps `entityType → source`, rejects duplicate entityTypes, returns the right source; a kind with no source is treated as un-payable. Step 2: Run (FAIL). Step 3: Implement the SPI + `SettlementContext` + a small `SettlementSourceRegistry` (`@ApplicationScoped`, builds the map from the injected `Instance`). Step 4: Run (PASS). Step 5: Add the pricing-time payee guard + a `CatalogPricingServiceAdapterTest` case proving an un-payable episode is `PriceUnavailable`. Step 6: Commit `feat(commerce): WU-COM-4 SettlementSource SPI + payee guard`.

---

## Task 9: podcasts `SettlementSource` — episode + season-pass (payee + episode expansion)

**Files:**
- Create: `podcasts/adapter/out/settlement/EpisodeSettlementSource.java`, `SeasonPassSettlementSource.java`
- Add to podcasts out port: an episode-ids-of-show read + a creator-of read (extend `PodcastRepository` if needed): `Optional<AccountId> creatorOfShow(PodcastId)`, `Optional<AccountId> creatorOfEpisode(EpisodeId)` (episode→podcast join), `List<String> episodeIdsOfShow(PodcastId)`.
- Test: `podcasts/adapter/out/settlement/PodcastSettlementSourceTest.java`, repository IT for the new reads.

**Decisions:**
- `EpisodeSettlementSource` (`entityType="episode"`): `payee` = `creatorOfEpisode(refId)`; `ownedEpisodeIds` = `List.of(refId)` (the single episode); `fulfill` = no-op (commerce writes the grant).
- `SeasonPassSettlementSource` (`entityType="season-pass"`): `payee` = `creatorOfShow(refId)`; `ownedEpisodeIds` = `episodeIdsOfShow(refId)` — **the season→episodes expansion that exists nowhere today** (mirrors album→tracks, INV-2). A season pass grants ownership of every current episode of the show. Document the "episodes added after purchase" question in the ADR (default: grants the set at settlement time; later episodes are not retroactively granted — matches album semantics).
- These are reads on podcasts' own tables → no cross-module read by commerce.

- [ ] TDD per source: failing test (payee resolves to the podcast creator; season expansion returns all episode ids; nullable creator → empty) → implement repo reads + sources → pass → commit `feat(podcasts): WU-COM-4 episode/season-pass settlement source`.

---

## Task 10: events `SettlementSource` — ticket (payee + IssueTicket fulfillment)

**Files:**
- Create: `events/adapter/out/settlement/TicketSettlementSource.java`
- Test: `events/adapter/out/settlement/TicketSettlementSourceTest.java`, `events/it/TicketSettlementIT.java`

**Decisions:**
- `entityType="ticket"`; `payee` = event `artist_id` resolved from the `refId` event segment (add `Optional<AccountId> organizerOf(EventId)` to the events read port); `ownedEpisodeIds` = empty; `fulfill` = parse `refId` `"eventId:tierId"` (SAME parser resolved in Task 3), build `IssueTicketCommand(eventId, tierId, ctx.orderId(), ctx.buyer(), holderName, ctx.qty(), IdempotencyKey.of(ctx.idempotencyKey()))`, call the injected `IssueTicket` port. Holder name: from the buyer's identity profile if available, else a placeholder — confirm what `IssueTicketCommand.holderName` requires and where to source it (grep identity for a name read; if none cheaply available, pass the account id / a stable placeholder and note it).
- **Tickets are NOT ownership grants** — no `ownership_grant` row (the table's track-XOR-episode CHECK forbids it). Fulfillment is the minted `ticket` rows via `IssueTicket`, which is already idempotent on `(orderId,tierId)`.
- **Tx boundary:** `IssueTicketService` is `@Transactional`. It is invoked from within `GrantOwnershipService.grantForSettledOrder` (`@Transactional(REQUIRES_NEW)`). Confirm the propagation is correct so a ticket-mint failure rolls back / is isolated as intended, and that the AFTER_SUCCESS observer semantics still hold (see the analytics-observer-tx precedent — an AFTER_SUCCESS observer needing its own tx uses `REQUIRES_NEW`). Decide and document whether ticket minting shares the grant tx (all-or-nothing per order) or is `REQUIRES_NEW` (independent, relies on its own idempotency). Default: share the grant tx so a mint failure fails the whole settlement fan-out and is retried by redelivery.

- [ ] TDD: source unit test (payee, refId parse, command build via a fake `IssueTicket`) + an IT that settles a ticket order and asserts a `ticket` row is minted, the tier `sold` incremented, and the split posted. Commit `feat(events): WU-COM-4 ticket settlement source + issuance wiring`.

---

## Task 11: store `SettlementSource` — payee + stock fulfillment (retire the dormant subscriber)

**Files:**
- Create: `store/adapter/out/settlement/StoreSettlementSource.java`
- Modify/remove: `store/adapter/in/events/PurchaseConfirmedSubscriber.java` (dormant; its stock-decrement logic moves into `fulfill` with the REAL qty, replacing the hard-coded `1`).
- Test: `store/adapter/out/settlement/StoreSettlementSourceTest.java`, `store/it/StoreSettlementIT.java`

**Decisions:**
- `entityType="store"`; `payee` = `store_item.artist_id` (add `Optional<AccountId> sellerOf(StoreItemId)` to the store read port); `ownedEpisodeIds` = empty; `fulfill` = for a stock-bearing item (`stockRemaining` present) call `storeRepository.decrementStock(storeItemId, ctx.qty())` — the existing atomic conditional decrement, now with the true qty. Parse the `refId` note off (`item-1:M` → `item-1`) exactly as Task 4.
- **Store "ownership":** store purchases are recorded by the `Order`/`order_line` themselves (the receipt) + stock decrement + revenue split; there is no `ownership_grant` row for store (not a track/episode) and no library-entitlement surface in scope. Note this explicitly in the ADR (a store-library/entitlement read is a separate future concern).
- Retire `PurchaseConfirmedSubscriber` (or gut it to a no-op with a deprecation note) so store stock is decremented exactly once via the SPI `fulfill`, not twice. Confirm no other consumer depends on it.

- [ ] TDD: source unit test (payee, refId strip, qty-correct decrement via fake repo) + IT settling a store order (stock drops by qty, split posted, no double-decrement). Commit `feat(store): WU-COM-4 store settlement source + retire dormant subscriber`.

---

## Task 12: Wire the SPI into `GrantOwnershipService` (dispatch episode grants + fulfill + split)

**Files:**
- Modify: `commerce/application/service/GrantOwnershipService.java`, `commerce/domain/OwnershipGrant.java` usage (`forEpisode`), and the per-line loop to populate `grantedEpisodeIds`.
- Modify: `commerce/adapter/out/persistence/CatalogExpansionReaderAdapter.java` — leave track/album path; the four new kinds now resolve via `SettlementSource` (inject the registry into `GrantOwnershipService`, not the catalog reader, to keep the catalog reader catalog-only).
- Test: `commerce/application/GrantOwnershipServiceTest.java` (extend), `commerce/it/SettlementFulfillmentIT.java` (new, per kind + mixed cart).

**Decisions:**
- In the per-line loop, dispatch by kind:
  - `track`/`album`/`album_rest` → existing `tracksToGrant` + `creatorOf` (unchanged).
  - `episode`/`season_pass` → `SettlementSource.ownedEpisodeIds(refId)` → for each episode id, dedup `existsActiveForEpisode`, `ownershipRepository.save(OwnershipGrant.forEpisode(...))`, add to `grantedEpisodeIds`; payee = `SettlementSource.payee(refId)` → accumulate gross by creator; then `fulfill(ctx)` (no-op).
  - `ticket`/`store` → payee = `SettlementSource.payee(refId)` → accumulate gross; then `SettlementSource.fulfill(ctx)` (mint / stock). No ownership rows.
- Populate `grantedEpisodeIds` into the `OwnershipGranted` event (currently always empty). Keep the single per-creator `postSaleSplit` loop — it is already kind-agnostic; it just needs the four kinds' gross to reach `grossByCreator`.
- **Payee-absent at settlement** (should be impossible after the Task 8 guard, but defend in depth): if `payee` is empty for a settled line, do NOT silently drop the gross — append an `AuditEntry` (INV-10) and post the line gross to `PLATFORM_REVENUE` only (add a `LedgerPostingService.postPlatformOnly(...)` or equivalent), so funds are always accounted (INV-4/INV-6). Flag for the security reviewer.
- Preserve exactly-once: all of this stays inside the existing `claimGrantPosting(order.getId())` guard so a redelivered settlement is a no-op.

- [ ] TDD: extend `GrantOwnershipServiceTest` with fakes for `SettlementSource` (episode grant emitted; ticket `fulfill` called once; split gross includes the new kinds; redelivery no-ops). Then `SettlementFulfillmentIT` end-to-end per kind + a mixed track+ticket cart proving BOTH lines post to the ledger (the exact bug the research surfaced). Commit `feat(commerce): WU-COM-4 settlement dispatch for episode/season-pass/ticket/store`.

---

## Task 13: Un-gate checkout (the final, guarded flip)

**Files:**
- Modify: `commerce/application/service/CheckoutService.java` — `gateKind(...)`.
- Test: `commerce/application/CheckoutServiceTest.java`, `commerce/it/CheckoutFlowIT.java` (extend), `commerce/it/CheckoutFulfillmentE2EIT.java` (new — full charge→settle→own/mint/split per kind).

**Decisions:**
- Remove the four kinds from the `gateKind` 409 throw so they proceed through authoritative re-pricing → charge → settlement fulfillment. Keep the method (it may still reject a genuinely unknown/again-gated kind, and it documents intent).
- **Only flip after Tasks 8–12 are green.** The E2E IT is the gate: for each kind, a settled order must (a) grant/mint the right thing, (b) post a balanced 70/30 split crediting the resolved creator, (c) be idempotent under a duplicate webhook. Mixed-cart E2E must show no line's revenue is lost.
- The old `CheckoutFlowIT` assertion that these kinds return 409 becomes a negative-that-flips: update it to assert they now succeed (202) and settle correctly; add a fresh negative case for a still-unsupported/unknown kind if one remains.

- [ ] TDD: flip, update the gate ITs, add the E2E fulfillment IT per kind. Commit `feat(commerce): WU-COM-4 un-gate episode/season-pass/ticket/store checkout`.

---

## Task 14: Docs + ADR + backlog

**Files:**
- Modify: `API-CONTRACT.md`, `backend/docs/00-system-architecture.md` (§9 ADR), `backend/docs/architecture/commerce.md`, `backend/docs/architecture/payments.md`, `backend/.project/backlog.yaml`

- [ ] **Step 1:** `API-CONTRACT.md` — on the `/checkout` (OrderSnapshot) response add `checkoutUrl?: string | null` with the WU-PAY-6 semantics note ("non-null only for a card charge requiring a Redde hosted-checkout redirect; null for MoMo/sandbox").
- [ ] **Step 2:** ADR (next free number in §9): "Cross-module authoritative pricing via a commerce-declared `ModulePriceSource` SPI and settlement fulfillment/payee via a `SettlementSource` SPI, both implemented by owning modules (edge `module → commerce`, no cycle; mirrors WU-SRCH-2 `IndexSource`). `checkoutUrl` threaded `PaymentIntentView → ChargeResult → CheckoutResult` and persisted on `Order`. Checkout **un-gated** for episode/season-pass/ticket/store once each kind's settlement path (ownership grant / ticket mint / store stock + 70/30 split, all under the existing exactly-once claim) is wired; a pricing-time payee guard prevents an un-payable item from ever settling into unaccounted funds. Season pass grants the show's current episode set at settlement (album-like, not retroactive). Store purchases are recorded by the order + stock decrement + split (no ownership-grant row; store-library entitlement out of scope)." Record the resolved ticket-refId convention (Task 3) and the ticket-mint tx-boundary decision (Task 10).
- [ ] **Step 3:** commerce.md + payments.md + `backend/docs/architecture/{podcasts,events,store}.md` as-built notes (each now implements a commerce SPI).
- [ ] **Step 4:** `backlog.yaml` — flip `WU-COM-4` to `in_review` (was `in_progress` since branch start). Scope B is fully contained in this WU, so **no follow-up stub** — this closes the commerce module. Board becomes 46/46 on merge.
- [ ] **Step 5: Commit** — `docs(commerce): WU-COM-4 ADR + API-CONTRACT + ADDs + backlog`.

---

## Verification

- **Never run `verify.sh`/`smoke.sh` yourself** — the user runs them (IntelliJ JPS races the build). After each task, tell the user the gate command and wait for their PASS report:
  `bash backend/scripts/verify.sh && bash backend/scripts/smoke.sh`
- **Repo test-tooling traps (all silently pass while running nothing):** ArchUnit runs **in-suite** (`-Dgroups=arch` → 0 tests); ITs need `-DskipITs=false -DskipUnitTests=true` because `<skipITs>true</skipITs>` is the pom default — after any IT run confirm `backend/target/failsafe-reports/*.xml` exists. Frontend is untouched by this WU.
- **Regression (the point of the WU):** the add-to-cart spoof test (Task 4 IT) must prove a client `metadata.priceMinor` is ignored for all four kinds. Prove it has teeth by temporarily returning the spoofed price and watching the test fail.
- **End-to-end money proof (the crux):** for each un-gated kind, a `charge → settle → fulfill` IT must assert (a) the right fulfillment (episode/season ownership grant, ticket minted + tier `sold++`, store stock decremented by the true qty), (b) a **balanced** 70/30 split crediting the resolved creator (INV-6 DB trigger + in-app assert), and (c) idempotency under a duplicate `PaymentSettled` (no double grant/mint/decrement, no double split). A **mixed-cart** IT (track + ticket) must prove BOTH lines reach the ledger — the exact silent-drop bug the research surfaced.
- **Payee guard:** an IT must prove an item with a null creator/artist/seller is un-buyable (fails at pricing, never charged).
- **checkoutUrl null-by-default:** with `PSP_REDDE` off (default) every checkout returns `checkoutUrl: null`.
- **Human gate:** security-reviewer sign-off REQUIRED before merge (money settlement path; INV-1/4/6/9/10). Deploy remains gated on Redde credentials regardless.

## Self-review notes (author)

- **Scope:** this is the full **Scope B** — Tasks 1–7 (pricing SPI + checkoutUrl) build the safe foundation; Tasks 8–13 wire settlement fulfillment + split for all four kinds and un-gate checkout as the final guarded step; Task 14 documents + closes the backlog. The gate is flipped ONLY in Task 13, after every fulfillment path is green.
- **Ordering safety:** Tasks 1–4 leave the four kinds un-priced mid-branch (they throw), which is fine because the gate still rejects them at checkout until Task 13. Never open the PR before Task 13's E2E ITs are green. Consider splitting the PR if it grows unwieldy — but the un-gate (Task 13) must not merge without its fulfillment tasks (8–12) in the same release.
- **Shared open question (Tasks 3 & 10):** the `ticket` refId second segment (`TicketTierId` vs tier name) MUST be parsed identically by the price source (Task 3) and the issuance fulfillment (Task 10) — resolve once, use everywhere. A mismatch prices one tier and mints another.
- **Tx-boundary care (Task 10/12):** ticket minting and store decrement run inside the `GrantOwnershipService` settlement fan-out (`@Transactional(REQUIRES_NEW)`, AFTER_SUCCESS observer). Follow the analytics-observer-tx precedent; default is to share the grant tx (all-or-nothing per order) so redelivery re-runs the whole fan-out under the same exactly-once claim.
- **Type consistency:** `PricedItem(title, subtitle, image, unitPrice)` used identically everywhere; `ChargeResult` (3-arg) / `CheckoutResult` (5-arg) arities updated at every construction site; `SettlementSource`/`ModulePriceSource` both discovered via CDI `Instance<>` exactly like `ReindexService`'s `Instance<IndexSource>`.
