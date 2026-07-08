package org.shakvilla.beatzmedia.events.domain;

/** Typed wrapper for a {@link Ticket}'s primary key (opaque string). Events ADD §3. */
public record TicketId(String value) {

  public TicketId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TicketId value must not be blank");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
