package org.shakvilla.beatzmedia.events.domain;

/**
 * Live availability status of an {@link Event}, ALWAYS derived from tier capacity/sold counters —
 * never stored as a display string (INV-EVT-2). Lifted verbatim from the {@code EventStatus}
 * TypeScript union in {@code Frontend/src/types/index.ts}. Events ADD §3 / §9.
 */
public enum EventStatus {
  ON_SALE("on-sale"),
  SELLING_FAST("selling-fast"),
  SOLD_OUT("sold-out");

  private final String wireValue;

  EventStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  /** The exact wire string used by the frontend/API (e.g. {@code "selling-fast"}). */
  public String wireValue() {
    return wireValue;
  }
}
