package org.shakvilla.beatzmedia.admin.domain;

/**
 * Lifecycle status of a {@link SupportTicket}. Wire values are lowercase, matching {@code
 * TicketStatus} in {@code Frontend/src/lib/admin-data.ts}. Admin ADD §3.
 */
public enum TicketStatus {
  OPEN("open"),
  PENDING("pending"),
  RESOLVED("resolved");

  private final String wireValue;

  TicketStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /** Parses a lowercase wire value. Throws {@link InvalidTicketStatusException} if unrecognised. */
  public static TicketStatus fromWireValue(String value) {
    if (value == null) {
      throw new InvalidTicketStatusException("Ticket status must not be null");
    }
    for (TicketStatus s : values()) {
      if (s.wireValue.equalsIgnoreCase(value)) {
        return s;
      }
    }
    throw new InvalidTicketStatusException("Unknown ticket status: " + value);
  }
}
