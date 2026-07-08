package org.shakvilla.beatzmedia.events.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TicketTier} — INV-EVT-1 (no oversell) and the {@code soldOut} derivation
 * (INV-EVT-2). Events ADD §11.
 */
@Tag("unit")
class TicketTierTest {

  private static TicketTier tier(int capacity, int sold) {
    return new TicketTier(
        new TicketTierId("tier-1"), new EventId("event-1"), "Regular", 15000, List.of(), capacity, sold);
  }

  // ---- construction guard: 0 <= sold <= capacity (INV-EVT-1) ----

  @Test
  void constructor_soldExceedsCapacity_throws() {
    assertThrows(IllegalArgumentException.class, () -> tier(10, 11));
  }

  @Test
  void constructor_negativeSold_throws() {
    assertThrows(IllegalArgumentException.class, () -> tier(10, -1));
  }

  @Test
  void constructor_negativeCapacity_throws() {
    assertThrows(IllegalArgumentException.class, () -> tier(-1, 0));
  }

  // ---- isSoldOut() / remaining() derivation (INV-EVT-2) ----

  @Test
  void isSoldOut_soldLessThanCapacity_isFalse() {
    assertFalse(tier(10, 9).isSoldOut());
  }

  @Test
  void isSoldOut_soldEqualsCapacity_isTrue() {
    assertTrue(tier(10, 10).isSoldOut());
  }

  @Test
  void remaining_computesCapacityMinusSold() {
    assertEquals(3, tier(10, 7).remaining());
  }

  // ---- sell(quantity): pure capacity guard (INV-EVT-1) ----

  @Test
  void sell_withinCapacity_increasesSold() {
    TicketTier after = tier(10, 5).sell(3);
    assertEquals(8, after.sold());
    assertFalse(after.isSoldOut());
  }

  @Test
  void sell_exactlyToCapacity_marksSoldOut() {
    TicketTier after = tier(10, 8).sell(2);
    assertEquals(10, after.sold());
    assertTrue(after.isSoldOut());
  }

  @Test
  void sell_pastCapacity_throwsTierSoldOutException() {
    TicketTier nearlyFull = tier(10, 9);
    assertThrows(TierSoldOutException.class, () -> nearlyFull.sell(2));
  }

  @Test
  void sell_alreadySoldOut_throwsTierSoldOutException() {
    TicketTier full = tier(10, 10);
    assertThrows(TierSoldOutException.class, () -> full.sell(1));
  }

  @Test
  void sell_nonPositiveQuantity_throws() {
    TicketTier t = tier(10, 5);
    assertThrows(IllegalArgumentException.class, () -> t.sell(0));
    assertThrows(IllegalArgumentException.class, () -> t.sell(-1));
  }

  @Test
  void sell_doesNotMutateOriginal() {
    TicketTier original = tier(10, 5);
    original.sell(3);
    assertEquals(5, original.sold(), "sell() must be a pure function");
  }
}
