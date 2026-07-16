package org.shakvilla.beatzmedia.events.adapter.out.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.SettlementContext;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicket;
import org.shakvilla.beatzmedia.events.application.port.in.IssueTicketCommand;
import org.shakvilla.beatzmedia.events.application.port.in.TicketIssued;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.fakes.FakeEventRepository;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Unit tests for the WU-COM-4 ticket {@code SettlementSource}. */
class TicketSettlementSourceTest {

  /** Capturing fake {@link IssueTicket}. */
  private static final class CapturingIssueTicket implements IssueTicket {
    IssueTicketCommand captured;

    @Override
    public TicketIssued issue(IssueTicketCommand command) {
      this.captured = command;
      return new TicketIssued(List.of(), List.of(), false);
    }
  }

  private static Event event(String artistId) {
    return new Event(
        new EventId("evt-1"), "Iron Boy Live", "Black Sherif", artistId, List.of("Lasmid"),
        "img.png", Instant.parse("2026-07-09T19:00:00Z"), "7:00 PM", "Independence Square", "Accra",
        "Greater Accra", EventCategory.CONCERT, "desc", "All ages", 99,
        List.of(
            new TicketTier(
                new TicketTierId("evt-1-vip"), new EventId("evt-1"), "VIP", 40000, List.of("perk"),
                200, 100)));
  }

  private static SettlementContext ctx(String refId) {
    return new SettlementContext(
        refId,
        new org.shakvilla.beatzmedia.commerce.domain.OrderId("order-1"),
        new AccountId("buyer-1"),
        2);
  }

  @Test
  void payee_isEventArtist() {
    var repo = new FakeEventRepository().withEvent(event("black-sherif"));
    var source = new TicketSettlementSource(repo, new CapturingIssueTicket());

    assertEquals(new AccountId("black-sherif"), source.payee("evt-1:VIP").orElseThrow());
    assertEquals("ticket", source.entityType());
  }

  @Test
  void payee_emptyWhenNoArtistOrMalformedRef() {
    var repo = new FakeEventRepository().withEvent(event(null));
    var source = new TicketSettlementSource(repo, new CapturingIssueTicket());

    assertFalse(source.payee("evt-1:VIP").isPresent());
    assertFalse(source.payee("no-colon").isPresent());
  }

  @Test
  void fulfill_mintsTicketForResolvedTier() {
    var repo = new FakeEventRepository().withEvent(event("black-sherif"));
    var issuer = new CapturingIssueTicket();
    var source = new TicketSettlementSource(repo, issuer);

    source.fulfill(ctx("evt-1:VIP"));

    assertEquals(new EventId("evt-1"), issuer.captured.eventId());
    assertEquals(new TicketTierId("evt-1-vip"), issuer.captured.tierId());
    assertEquals("order-1", issuer.captured.orderId().value());
    assertEquals(new AccountId("buyer-1"), issuer.captured.holderAccountId());
    assertEquals(2, issuer.captured.quantity());
  }

  @Test
  void fulfill_unknownTier_throws() {
    var repo = new FakeEventRepository().withEvent(event("black-sherif"));
    var source = new TicketSettlementSource(repo, new CapturingIssueTicket());

    assertThrows(IllegalStateException.class, () -> source.fulfill(ctx("evt-1:Nope")));
  }
}
