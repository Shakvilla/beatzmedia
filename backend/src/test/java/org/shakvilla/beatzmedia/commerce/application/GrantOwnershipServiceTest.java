package org.shakvilla.beatzmedia.commerce.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementSource;
import org.shakvilla.beatzmedia.commerce.application.service.GrantOwnershipService;
import org.shakvilla.beatzmedia.commerce.application.service.SettlementSourceRegistry;
import org.shakvilla.beatzmedia.commerce.domain.CartItemKind;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OrderId;
import org.shakvilla.beatzmedia.commerce.domain.OrderLine;
import org.shakvilla.beatzmedia.commerce.domain.OrderStatus;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGranted;
import org.shakvilla.beatzmedia.commerce.domain.SaleRecorded;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCartRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeCatalogExpansionReader;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOrderRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeOwnershipRepository;
import org.shakvilla.beatzmedia.commerce.fakes.FakeSaleLedgerPoster;
import org.shakvilla.beatzmedia.commerce.fakes.RecordingEvent;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link GrantOwnershipService} (INV-1/INV-2/INV-4) — the settlement→ownership grant.
 * Proves: album expansion to constituent tracks (INV-2), the 70/30 sale split posting (INV-4), cart
 * clear, and — the load-bearing carryover — that a RE-DELIVERED settlement grants exactly ONCE
 * (idempotency via the exactly-once claim). Also proves a non-settled order never grants (INV-1).
 */
@Tag("unit")
class GrantOwnershipServiceTest {

  private static final AccountId BUYER = new AccountId("buyer-1");
  private static final String CREATOR = "artist-1";
  private static final Instant NOW = Instant.parse("2026-07-03T10:00:00Z");

  FakeOrderRepository orders;
  FakeOwnershipRepository ownership;
  FakeCatalogExpansionReader expansion;
  FakeSaleLedgerPoster ledger;
  FakeCartRepository carts;
  FakeAuditWriter audit;
  RecordingEvent<OwnershipGranted> grantedEvent;
  RecordingEvent<SaleRecorded> saleRecordedEvent;
  GrantOwnershipService service;

  @BeforeEach
  void setUp() {
    orders = new FakeOrderRepository();
    ownership = new FakeOwnershipRepository();
    expansion = new FakeCatalogExpansionReader();
    ledger = new FakeSaleLedgerPoster();
    carts = new FakeCartRepository();
    audit = new FakeAuditWriter();
    grantedEvent = new RecordingEvent<>();
    saleRecordedEvent = new RecordingEvent<>();
    service = serviceWith(); // no SettlementSources → track/album catalog path (existing tests)
  }

  /** Build the service with the given owning-module {@link SettlementSource}s registered (WU-COM-4). */
  private GrantOwnershipService serviceWith(SettlementSource... sources) {
    return new GrantOwnershipService(
        orders, ownership, expansion, new SettlementSourceRegistry(List.of(sources)), ledger, carts,
        audit, grantedEvent, saleRecordedEvent, FakeIds.sequential("grant"), FakeClock.fixed());
  }

  /** Capturing fake {@link SettlementSource} for the WU-COM-4 settlement dispatch tests. */
  private static final class FakeSettlementSource implements SettlementSource {
    private final String entityType;
    private final String payeeId; // null → no payee
    private final List<String> episodes;
    int fulfillCount = 0;

    FakeSettlementSource(String entityType, String payeeId, List<String> episodes) {
      this.entityType = entityType;
      this.payeeId = payeeId;
      this.episodes = episodes;
    }

    @Override
    public String entityType() {
      return entityType;
    }

    @Override
    public Optional<AccountId> payee(String refId) {
      return payeeId == null ? Optional.empty() : Optional.of(new AccountId(payeeId));
    }

    @Override
    public List<String> ownedEpisodeIds(String refId) {
      return episodes;
    }

    @Override
    public void fulfill(SettlementContext ctx) {
      fulfillCount++;
    }
  }

  private OrderLine episodeLine(String episodeId, long priceMinor) {
    return new OrderLine(
        "l-" + episodeId, CartItemKind.episode, episodeId, "Ep", "Show", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  private OrderLine ticketLine(String refId, long priceMinor, int qty) {
    return new OrderLine(
        "l-" + refId, CartItemKind.ticket, refId, "Event", "VIP", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), qty);
  }

  // ---- WU-COM-4 settlement dispatch (episode/ticket via SettlementSource) ----

  @Test
  void settlement_episode_grantsEpisodeOwnership_andCreditsCreator() {
    var source = new FakeSettlementSource("episode", "podcast-creator", List.of("ep-1"));
    GrantOwnershipService svc = serviceWith(source);
    pendingOrder("EP1", episodeLine("ep-1", 500));

    svc.grantForSettledOrder("EP1", "intent-EP1", "mtn");

    assertTrue(ownership.existsActiveForEpisode(BUYER, "ep-1"), "episode ownership granted (INV-1)");
    assertEquals(1, ledger.countForCreator("podcast-creator"), "one 70/30 split to the show creator");
  }

  @Test
  void settlement_ticket_invokesFulfill_andCreditsArtist_withQtyGross() {
    var source = new FakeSettlementSource("ticket", "event-artist", List.of());
    GrantOwnershipService svc = serviceWith(source);
    pendingOrder("TK1", ticketLine("evt-1:VIP", 40000, 2));

    svc.grantForSettledOrder("TK1", "intent-TK1", "mtn");

    assertEquals(1, source.fulfillCount, "ticket minted exactly once via fulfill");
    assertEquals(1, ledger.countForCreator("event-artist"));
    assertEquals(80000, ledger.postings().get(0).grossMinor(), "gross = unitPrice(40000) × qty(2)");
    assertFalse(ownership.existsActiveForEpisode(BUYER, "evt-1:VIP"), "a ticket is not an ownership row");
  }

  @Test
  void settlement_ticket_redelivered_fulfillsAndSplitsExactlyOnce() {
    var source = new FakeSettlementSource("ticket", "event-artist", List.of());
    GrantOwnershipService svc = serviceWith(source);
    pendingOrder("TK2", ticketLine("evt-1:VIP", 40000, 1));

    svc.grantForSettledOrder("TK2", "intent-TK2", "mtn");
    svc.grantForSettledOrder("TK2", "intent-TK2", "mtn"); // webhook + poll re-delivery

    assertEquals(1, source.fulfillCount, "fulfill once despite redelivery (exactly-once claim)");
    assertEquals(1, ledger.countForCreator("event-artist"), "one split despite redelivery");
  }

  private Order pendingOrder(String ref, OrderLine... lines) {
    Order order =
        Order.create(
            new OrderId("o-" + ref),
            BUYER,
            ref,
            List.of(lines),
            Money.ofMinor(50, Currency.GHS),
            Currency.GHS,
            NOW);
    order.bindIdempotency("key-" + ref, "hash-" + ref);
    order.attachPaymentIntent("intent-" + ref);
    return orders.save(order);
  }

  private OrderLine trackLine(String trackId, long priceMinor) {
    return new OrderLine(
        "l-" + trackId, CartItemKind.track, trackId, "Track", "Artist", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  private OrderLine albumLine(String albumId, long priceMinor) {
    return new OrderLine(
        "l-" + albumId, CartItemKind.album, albumId, "Album", "Artist", "img.jpg",
        Money.ofMinor(priceMinor, Currency.GHS), 1);
  }

  @Test
  void grant_trackPurchase_grantsOwnership_paysCreator_clearsCart() {
    pendingOrder("BZ-2026-00001", trackLine("t1", 1000));
    expansion.seedTrack("t1", CREATOR);
    carts.save(new org.shakvilla.beatzmedia.commerce.domain.Cart(
        new org.shakvilla.beatzmedia.commerce.domain.CartId("c1"), BUYER, List.of()));

    service.grantForSettledOrder("BZ-2026-00001", "intent-BZ-2026-00001", "mtn");

    assertEquals(1, ownership.activeTrackCount(BUYER), "one track grant (INV-1)");
    assertTrue(ownership.existsActiveForTrack(BUYER, "t1"));
    assertEquals(OrderStatus.paid, orders.findByReference("BZ-2026-00001").orElseThrow().getStatus());
    assertEquals(1, ledger.count(), "one sale split posted (INV-4)");
    assertEquals(CREATOR, ledger.postings().get(0).creator());
    assertEquals(1000, ledger.postings().get(0).grossMinor());
    assertFalse(carts.hasCart(BUYER), "cart cleared on settlement");
    assertEquals(1, grantedEvent.count(), "OwnershipGranted fired once");
    assertEquals(1, saleRecordedEvent.count(), "SaleRecorded fired once for analytics (WU-ANA-1)");
    assertEquals(CREATOR, saleRecordedEvent.fired().get(0).creatorAccountId());
    assertEquals(1000, saleRecordedEvent.fired().get(0).grossMinor());
  }

  @Test
  void grant_albumPurchase_expandsToAllConstituentTracks_INV2() {
    pendingOrder("BZ-2026-00002", albumLine("al1", 3000));
    expansion.seedAlbum("al1", CREATOR, List.of("at1", "at2", "at3"));

    service.grantForSettledOrder("BZ-2026-00002", "intent-BZ-2026-00002", "mtn");

    assertEquals(3, ownership.activeTrackCount(BUYER), "album expands to 3 track grants (INV-2)");
    assertTrue(ownership.existsActiveForTrack(BUYER, "at1"));
    assertTrue(ownership.existsActiveForTrack(BUYER, "at2"));
    assertTrue(ownership.existsActiveForTrack(BUYER, "at3"));
    // Creator credited 70% of the whole album price (split % applied inside payments).
    assertEquals(3000, ledger.postings().get(0).grossMinor());
  }

  @Test
  void grant_multiCreatorOrder_creditsEachCreatorOnce_grantsAllTracks_F1() {
    // Two tracks from TWO DISTINCT creators in one order (a completely normal two-artist cart). The
    // ORIGINAL F1 bug: both lines posted with the same paymentIntentId → the 2nd collided on the
    // ledger_posting PK → DuplicatePostingException poisoned the grant txn → nothing granted, buyer
    // charged. FakeSaleLedgerPoster now throws DuplicatePostingException on a duplicate (intent, refId),
    // so a regression here would fail. With the fix (per-creator-unique ref), both post cleanly.
    pendingOrder(
        "BZ-2026-00010", trackLine("ta", 1000), trackLine("tb", 500));
    expansion.seedTrack("ta", "creator-A");
    expansion.seedTrack("tb", "creator-B");

    service.grantForSettledOrder("BZ-2026-00010", "intent-BZ-2026-00010", "mtn");

    // Both tracks granted, order paid.
    assertEquals(2, ownership.activeTrackCount(BUYER), "both purchased tracks granted (INV-2)");
    assertTrue(ownership.existsActiveForTrack(BUYER, "ta"));
    assertTrue(ownership.existsActiveForTrack(BUYER, "tb"));
    assertEquals(OrderStatus.paid, orders.findByReference("BZ-2026-00010").orElseThrow().getStatus());
    // Exactly ONE split per creator, each with its own gross, distinct source refs (no PK collision).
    assertEquals(2, ledger.count(), "one sale split per creator (F1)");
    assertTrue(
        ledger.postings().stream().anyMatch(p -> p.creator().equals("creator-A") && p.grossMinor() == 1000));
    assertTrue(
        ledger.postings().stream().anyMatch(p -> p.creator().equals("creator-B") && p.grossMinor() == 500));
    assertEquals(
        2L,
        ledger.postings().stream().map(FakeSaleLedgerPoster.Posting::refId).distinct().count(),
        "distinct-creator postings use distinct source refs (intentId:creatorId)");
    assertEquals(1, grantedEvent.count(), "OwnershipGranted fired once");
    assertEquals(2, saleRecordedEvent.count(), "one SaleRecorded per distinct creator");
  }

  @Test
  void grant_multiLineSameCreator_postsOneAggregatedSplit_F1() {
    // Two tracks from the SAME creator in one order must aggregate into ONE split (their gross summed),
    // not two postings with the same (intent, creator) ref that would collide.
    pendingOrder("BZ-2026-00011", trackLine("tx", 400), trackLine("ty", 600));
    expansion.seedTrack("tx", "creator-Z");
    expansion.seedTrack("ty", "creator-Z");

    service.grantForSettledOrder("BZ-2026-00011", "intent-BZ-2026-00011", "mtn");

    assertEquals(2, ownership.activeTrackCount(BUYER));
    assertEquals(1, ledger.count(), "same-creator lines aggregate into ONE split (F1)");
    assertEquals("creator-Z", ledger.postings().get(0).creator());
    assertEquals(1000, ledger.postings().get(0).grossMinor(), "gross summed (400 + 600)");
  }

  @Test
  void grant_multiCreatorRedelivery_creditsEachOnce_F1() {
    // A re-delivered settlement for a multi-creator order must still credit each creator exactly once
    // (the order-level grant claim short-circuits the whole re-delivery before any posting).
    pendingOrder("BZ-2026-00012", trackLine("tp", 800), trackLine("tq", 200));
    expansion.seedTrack("tp", "creator-P");
    expansion.seedTrack("tq", "creator-Q");

    service.grantForSettledOrder("BZ-2026-00012", "intent-BZ-2026-00012", "mtn");
    service.grantForSettledOrder("BZ-2026-00012", "intent-BZ-2026-00012", "mtn");

    assertEquals(2, ownership.activeTrackCount(BUYER), "each track granted once");
    assertEquals(2, ledger.count(), "each creator credited exactly once despite re-delivery");
  }

  @Test
  void grant_multiCreatorPartialSplitFailureThenRetry_noDoubleCredit_grantedOnce_B() {
    // FINDING B — partial-failure atomicity. Delivery 1: creator-A's split commits (own REQUIRES_NEW
    // txn), then creator-B's split throws a NON-duplicate transient error → it propagates out of
    // grantForSettledOrder → the outer grant transaction rolls back (order pending, grants removed,
    // grant-posting claim released) BUT creator-A's committed credit survives (an orphaned credit).
    // Delivery 2 (retry): the claim is free so the grant runs again; creator-A's split now hits its
    // ALREADY-COMMITTED ledger_posting PK (intentId:creator-A) → DuplicatePostingException → swallowed
    // (benign) → creator-A is NOT double-credited; creator-B's transient failure has cleared so it
    // posts once. End state: each creator credited exactly once, both tracks granted once, order paid.
    pendingOrder("BZ-2026-00013", trackLine("ta", 1000), trackLine("tb", 500));
    expansion.seedTrack("ta", "creator-A");
    expansion.seedTrack("tb", "creator-B");
    ledger.failOnceForCreator("creator-B"); // creator-B fails on delivery 1 only

    // Delivery 1 — creator-B's transient failure propagates out (models the outer txn rollback).
    OrderId orderId = new OrderId("o-BZ-2026-00013");
    // A non-duplicate split failure propagates so the outer grant transaction rolls back.
    assertThrows(
        IllegalStateException.class,
        () -> service.grantForSettledOrder("BZ-2026-00013", "intent-BZ-2026-00013", "mtn"));
    // Reproduce the container rollback of the OUTER grant transaction on the in-memory fakes: the
    // claim + grants are undone; the order reverts to pending. Creator-A's committed split is NOT
    // rolled back (separate REQUIRES_NEW that already committed) — the orphaned credit.
    ownership.rollbackGrant(orderId);
    pendingOrder("BZ-2026-00013", trackLine("ta", 1000), trackLine("tb", 500)); // order back to pending
    assertEquals(1, ledger.countForCreator("creator-A"), "creator-A already credited once (orphaned)");
    assertEquals(0, ledger.countForCreator("creator-B"), "creator-B not credited on the failed delivery");

    // Delivery 2 (retry) — succeeds; creator-A's re-post is a benign duplicate (no double-credit).
    service.grantForSettledOrder("BZ-2026-00013", "intent-BZ-2026-00013", "mtn");

    assertEquals(1, ledger.countForCreator("creator-A"), "creator-A credited EXACTLY ONCE (no double)");
    assertEquals(1, ledger.countForCreator("creator-B"), "creator-B credited exactly once after retry");
    assertEquals(2, ledger.count(), "exactly two committed splits total — ledger balanced (INV-6)");
    assertEquals(2, ownership.activeTrackCount(BUYER), "both tracks granted once");
    assertEquals(OrderStatus.paid, orders.findByReference("BZ-2026-00013").orElseThrow().getStatus());
  }

  @Test
  void grant_redeliveredSettlement_grantsExactlyOnce_idempotent() {
    pendingOrder("BZ-2026-00003", trackLine("t9", 500));
    expansion.seedTrack("t9", CREATOR);

    // First delivery grants; a second (re-delivered webhook + poll race) must be a no-op.
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");
    service.grantForSettledOrder("BZ-2026-00003", "intent-BZ-2026-00003", "mtn");

    assertEquals(1, ownership.activeTrackCount(BUYER), "exactly one grant despite 3 deliveries");
    assertEquals(1, ledger.count(), "exactly one sale split despite 3 deliveries (no double-credit)");
    assertEquals(1, grantedEvent.count(), "OwnershipGranted fired exactly once");
  }

  @Test
  void grant_failedOrder_neverGrants_INV1() {
    Order order = pendingOrder("BZ-2026-00004", trackLine("t1", 500));
    order.markFailed("declined");
    orders.save(order);
    expansion.seedTrack("t1", CREATOR);

    service.grantForSettledOrder("BZ-2026-00004", "intent-x", "mtn");

    assertEquals(0, ownership.activeTrackCount(BUYER), "a failed order never grants (INV-1)");
    assertEquals(0, ledger.count(), "no credit for a failed order");
  }

  @Test
  void grant_unknownReference_isNoop() {
    service.grantForSettledOrder("BZ-9999-99999", "intent-x", "mtn");
    assertEquals(0, ownership.all().size());
    assertEquals(0, grantedEvent.count());
  }
}
