package org.shakvilla.beatzmedia.events.domain;

import java.util.List;

/**
 * A ticket tier belonging to an {@link Event} (e.g. "Regular", "VIP"). Framework-free. {@code
 * soldOut} is ALWAYS derived from {@code sold >= capacity} — never stored as a display flag
 * (INV-EVT-2). Events ADD §3.
 */
public final class TicketTier {

  private final TicketTierId id;
  private final EventId eventId;
  private final String name;
  private final long priceMinor;
  private final List<String> perks;
  private final int capacity;
  private final int sold;

  public TicketTier(
      TicketTierId id,
      EventId eventId,
      String name,
      long priceMinor,
      List<String> perks,
      int capacity,
      int sold) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (priceMinor < 0) {
      throw new IllegalArgumentException("priceMinor must not be negative");
    }
    if (capacity < 0) {
      throw new IllegalArgumentException("capacity must not be negative");
    }
    if (sold < 0) {
      throw new IllegalArgumentException("sold must not be negative");
    }
    // INV-EVT-1: 0 <= sold <= capacity, DB CHECK-backstopped (ticket_tier.chk_sold).
    if (sold > capacity) {
      throw new IllegalArgumentException("sold (" + sold + ") must not exceed capacity (" + capacity + ")");
    }
    this.id = id;
    this.eventId = eventId;
    this.name = name;
    this.priceMinor = priceMinor;
    this.perks = perks == null ? List.of() : List.copyOf(perks);
    this.capacity = capacity;
    this.sold = sold;
  }

  public TicketTierId id() {
    return id;
  }

  public EventId eventId() {
    return eventId;
  }

  public String name() {
    return name;
  }

  public long priceMinor() {
    return priceMinor;
  }

  public List<String> perks() {
    return perks;
  }

  public int capacity() {
    return capacity;
  }

  public int sold() {
    return sold;
  }

  /** Remaining inventory (never negative — enforced by the constructor invariant). */
  public int remaining() {
    return capacity - sold;
  }

  /** INV-EVT-2: derived, never a stored display flag. */
  public boolean isSoldOut() {
    return sold >= capacity;
  }

  /**
   * Pure function: sell {@code quantity} seats from this tier, returning a new tier reflecting the
   * increased {@code sold} count. Rejects an oversell (INV-EVT-1) with {@link
   * TierSoldOutException}. Used both by domain unit tests and to compute the post-issuance state
   * the application service persists via {@code EventRepository#incrementSold}.
   */
  public TicketTier sell(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (sold + quantity > capacity) {
      throw new TierSoldOutException(id == null ? name : id.value());
    }
    return new TicketTier(id, eventId, name, priceMinor, perks, capacity, sold + quantity);
  }
}
