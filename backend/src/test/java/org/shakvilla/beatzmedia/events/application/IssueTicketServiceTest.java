package org.shakvilla.beatzmedia.events.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicketCommand;
import org.shakvilla.beatzmedia.events.application.port.in.TicketIssued;
import org.shakvilla.beatzmedia.events.application.service.IssueTicketService;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.events.domain.OrderId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.domain.TicketTierNotFoundException;
import org.shakvilla.beatzmedia.events.domain.TicketTierSoldOut;
import org.shakvilla.beatzmedia.events.domain.TierSoldOutException;
import org.shakvilla.beatzmedia.events.fakes.FakeEventRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the INTERNAL {@link IssueTicketService} port — INV-EVT-1 (no oversell), the
 * (orderId, tierId) idempotency guard, and the {@code TicketTierSoldOut} event firing on the seat
 * that tips a tier into sold-out. The true concurrent-oversell proof (OQ-11, row lock under
 * Postgres) is a Testcontainers IT — see {@code IssueTicketConcurrencyIT}. Events ADD §11.
 */
@Tag("unit")
class IssueTicketServiceTest {

  private static final EventId EVENT_ID = new EventId("evt-1");
  private static final TicketTierId TIER_ID = new TicketTierId("evt-1-regular");
  private static final AccountId HOLDER = new AccountId("acct-1");

  private FakeEventRepository repo;
  private FakeIds ids;
  private FakeClock clock;
  private RecordingEvent<TicketTierSoldOut> soldOutEvent;
  private IssueTicketService service;

  /** Minimal hand-rolled fake for the CDI {@link Event} SPI (fakes-over-mocks convention). */
  static class RecordingEvent<T> implements Event<T> {
    final List<T> fired = new ArrayList<>();

    @Override
    public void fire(T event) {
      fired.add(event);
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
      throw new UnsupportedOperationException();
    }
  }

  private static TicketTier tierWith(int capacity, int sold) {
    return new TicketTier(TIER_ID, EVENT_ID, "Regular", 15000, List.of(), capacity, sold);
  }

  private static IssueTicketCommand commandFor(String orderId, int quantity) {
    return new IssueTicketCommand(
        EVENT_ID, TIER_ID, new OrderId(orderId), HOLDER, "Ama Serwaa", quantity, new IdempotencyKey("idem-" + orderId));
  }

  @BeforeEach
  void setUp() {
    repo = new FakeEventRepository();
    ids = FakeIds.sequential("ticket");
    clock = FakeClock.fixed();
    soldOutEvent = new RecordingEvent<>();
    service = new IssueTicketService(repo, ids, clock, soldOutEvent);
  }

  @Test
  void issue_withinCapacity_mintsTicketsAndIncrementsSold() {
    repo.withTier(tierWith(10, 5)); // seed the tier via lockTierForUpdate map

    TicketIssued result = service.issue(commandFor("order-1", 2));

    assertEquals(2, result.ticketIds().size());
    assertEquals(2, result.qrRefs().size());
    assertFalse(result.tierNowSoldOut());
    assertEquals(7, repo.lockTierForUpdate(TIER_ID).orElseThrow().sold());
  }

  @Test
  void issue_lastSeats_marksTierNowSoldOut_andFiresEvent() {
    repo.withTier(tierWith(10, 8));

    TicketIssued result = service.issue(commandFor("order-2", 2));

    assertTrue(result.tierNowSoldOut());
    assertEquals(1, soldOutEvent.fired.size());
    assertEquals("evt-1", soldOutEvent.fired.get(0).eventId());
    assertEquals("evt-1-regular", soldOutEvent.fired.get(0).tierId());
  }

  @Test
  void issue_exceedsCapacity_throwsTierSoldOutException_noPartialMint() {
    repo.withTier(tierWith(10, 9));

    assertThrows(TierSoldOutException.class, () -> service.issue(commandFor("order-3", 2)));

    assertEquals(9, repo.lockTierForUpdate(TIER_ID).orElseThrow().sold());
    assertTrue(soldOutEvent.fired.isEmpty());
  }

  @Test
  void issue_alreadySoldOut_throwsTierSoldOutException() {
    repo.withTier(tierWith(10, 10));

    assertThrows(TierSoldOutException.class, () -> service.issue(commandFor("order-4", 1)));
  }

  @Test
  void issue_unknownTier_throwsTicketTierNotFoundException() {
    IssueTicketCommand command =
        new IssueTicketCommand(
            EVENT_ID,
            new TicketTierId("no-such-tier"),
            new OrderId("order-5"),
            HOLDER,
            "Ama Serwaa",
            1,
            new IdempotencyKey("idem-5"));

    assertThrows(TicketTierNotFoundException.class, () -> service.issue(command));
  }

  @Test
  void issue_redeliveredSettlement_isIdempotent_noDoubleMint_noDoubleDecrement() {
    repo.withTier(tierWith(10, 5));

    TicketIssued first = service.issue(commandFor("order-6", 2));
    assertEquals(7, repo.lockTierForUpdate(TIER_ID).orElseThrow().sold());

    TicketIssued replay = service.issue(commandFor("order-6", 2));

    assertEquals(first.ticketIds(), replay.ticketIds());
    assertEquals(first.qrRefs(), replay.qrRefs());
    // sold must NOT be double-counted on replay.
    assertEquals(7, repo.lockTierForUpdate(TIER_ID).orElseThrow().sold());
  }
}
