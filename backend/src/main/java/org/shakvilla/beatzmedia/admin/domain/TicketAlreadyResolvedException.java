package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when {@code resolve} is called on a ticket that is already {@code resolved}. Maps to HTTP
 * 409 {@code ILLEGAL_TRANSITION}. Admin ADD §9 / LLFR-ADMIN-08.1.
 */
public class TicketAlreadyResolvedException extends RuntimeException {

  public TicketAlreadyResolvedException(String ticketId) {
    super("Support ticket already resolved: " + ticketId);
  }
}
