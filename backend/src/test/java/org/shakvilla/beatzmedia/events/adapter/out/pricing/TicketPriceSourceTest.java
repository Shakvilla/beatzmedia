package org.shakvilla.beatzmedia.events.adapter.out.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.events.domain.EventCategory;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.TicketTier;
import org.shakvilla.beatzmedia.events.domain.TicketTierId;
import org.shakvilla.beatzmedia.events.fakes.FakeEventRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/** Unit tests for the WU-COM-4 ticket {@code ModulePriceSource}. Tier resolved by NAME. */
class TicketPriceSourceTest {

  private static TicketTier tier(String id, String name, long price, int capacity, int sold) {
    return new TicketTier(
        new TicketTierId(id), new EventId("evt-1"), name, price, List.of("perk"), capacity, sold);
  }

  private static Event event() {
    return new Event(
        new EventId("evt-1"), "Iron Boy Live", "Black Sherif", "black-sherif",
        List.of("Lasmid"), "img.png", Instant.parse("2026-07-09T19:00:00Z"), "7:00 PM",
        "Independence Square", "Accra", "Greater Accra", EventCategory.CONCERT, "desc", "All ages",
        99,
        List.of(
            tier("evt-1-vip", "VIP", 40000, 200, 195),
            tier("evt-1-sold", "Sold Out", 10000, 10, 10)));
  }

  private TicketPriceSource source() {
    return new TicketPriceSource(new FakeEventRepository().withEvent(event()));
  }

  @Test
  void ticket_pricesTierByName() {
    PricedItem priced = source().price("evt-1:VIP", Map.of());

    assertEquals("Iron Boy Live", priced.title());
    assertEquals("VIP", priced.subtitle());
    assertEquals("img.png", priced.image());
    assertEquals(Money.ofMinor(40000, Currency.GHS), priced.unitPrice());
    assertEquals("ticket", source().entityType());
  }

  @Test
  void ticket_soldOutTierIsNotPurchasable() {
    assertThrows(PriceUnavailableException.class, () -> source().price("evt-1:Sold Out", Map.of()));
  }

  @Test
  void ticket_unknownTierNameIsNotFound() {
    assertThrows(PriceUnavailableException.class, () -> source().price("evt-1:Nope", Map.of()));
  }

  @Test
  void ticket_unknownEventIsNotFound() {
    assertThrows(PriceUnavailableException.class, () -> source().price("no-event:VIP", Map.of()));
  }

  @Test
  void ticket_malformedRefIsNotFound() {
    assertThrows(PriceUnavailableException.class, () -> source().price("no-colon", Map.of()));
  }
}
