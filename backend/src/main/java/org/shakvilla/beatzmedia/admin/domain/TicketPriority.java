package org.shakvilla.beatzmedia.admin.domain;

/**
 * Priority of a {@link SupportTicket}. Wire values are lowercase, matching {@code TicketPriority}
 * in {@code Frontend/src/lib/admin-data.ts}. Admin ADD §3.
 */
public enum TicketPriority {
  HIGH("high"),
  NORMAL("normal"),
  LOW("low");

  private final String wireValue;

  TicketPriority(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  /**
   * Parses a lowercase wire value. Throws {@link InvalidTicketStatusException} if unrecognised
   * (reuses the same 422 error code as ticket status — both are query-filter validation errors).
   */
  public static TicketPriority fromWireValue(String value) {
    if (value == null) {
      throw new InvalidTicketStatusException("Ticket priority must not be null");
    }
    for (TicketPriority p : values()) {
      if (p.wireValue.equalsIgnoreCase(value)) {
        return p;
      }
    }
    throw new InvalidTicketStatusException("Unknown ticket priority: " + value);
  }
}
