package org.shakvilla.beatzmedia.admin.domain;

/**
 * Typed identity wrapper for a {@link SupportTicket}'s opaque string id. Admin ADD §3 /
 * LLFR-ADMIN-08.1.
 */
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
