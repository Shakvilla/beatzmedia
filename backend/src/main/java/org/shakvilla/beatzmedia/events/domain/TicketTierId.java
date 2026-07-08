package org.shakvilla.beatzmedia.events.domain;

/** Typed wrapper for a {@link TicketTier}'s primary key (opaque string). Events ADD §3. */
public record TicketTierId(String value) {

  public TicketTierId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TicketTierId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
