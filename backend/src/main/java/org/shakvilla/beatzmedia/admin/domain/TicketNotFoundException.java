package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a {@link SupportTicket} lookup by id finds nothing. Maps to HTTP 404 at the adapter
 * boundary. Admin ADD §9 / LLFR-ADMIN-08.1.
 */
public class TicketNotFoundException extends RuntimeException {

  public TicketNotFoundException(String ticketId) {
    super("Support ticket not found: " + ticketId);
  }
}
