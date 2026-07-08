package org.shakvilla.beatzmedia.events.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Event#status()} — INV-EVT-2: status is ALWAYS derived from live tier
 * availability, never a stored display string. Events ADD §3 / §11 (EVENTS-01.2 acceptance).
 */
@Tag("unit")
class EventStatusDerivationTest {

  private static TicketTier tier(String name, int capacity, int sold) {
    return new TicketTier(
        new TicketTierId("evt-" + name), new EventId("evt"), name, 15000, List.of(), capacity, sold);
  }

  private static Event eventWith(List<TicketTier> tiers) {
    return new Event(
        new EventId("evt"),
        "Some Event",
        "Some Artist",
        null,
        List.of(),
        "img.png",
        Instant.parse("2026-07-09T19:00:00Z"),
        null,
        "Venue",
        "Accra",
        null,
        EventCategory.CONCERT,
        null,
        null,
        50,
        tiers);
  }

  @Test
  void ampleRemainingStock_isOnSale() {
    Event event = eventWith(List.of(tier("regular", 1000, 100), tier("vip", 200, 20)));
    assertEquals(EventStatus.ON_SALE, event.status());
  }

  @Test
  void totalRemainingAtLowStockThreshold_isSellingFast() {
    // capacity 500/sold 480 (remain 20) + capacity 200/sold 170 (remain 30) = 50 remaining total.
    Event event = eventWith(List.of(tier("regular", 500, 480), tier("vip", 200, 170)));
    assertEquals(EventStatus.SELLING_FAST, event.status());
  }

  @Test
  void totalRemainingJustAboveLowStockThreshold_isOnSale() {
    // 51 remaining total: one more than the threshold.
    Event event = eventWith(List.of(tier("regular", 500, 480), tier("vip", 200, 169)));
    assertEquals(EventStatus.ON_SALE, event.status());
  }

  @Test
  void everyTierAtCapacity_isSoldOut() {
    Event event = eventWith(List.of(tier("regular", 500, 500), tier("vip", 200, 200)));
    assertEquals(EventStatus.SOLD_OUT, event.status());
  }

  @Test
  void oneTierSoldOut_otherAmpleStock_isOnSale() {
    Event event = eventWith(List.of(tier("regular", 500, 500), tier("vip", 200, 20)));
    assertEquals(EventStatus.ON_SALE, event.status());
  }

  @Test
  void oneTierSoldOut_otherLowStock_isSellingFast() {
    Event event = eventWith(List.of(tier("regular", 500, 500), tier("vip", 200, 190)));
    assertEquals(EventStatus.SELLING_FAST, event.status());
  }

  @Test
  void noTiers_isSoldOut() {
    Event event = eventWith(List.of());
    assertEquals(EventStatus.SOLD_OUT, event.status());
  }
}
