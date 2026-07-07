package org.shakvilla.beatzmedia.admin.domain;

/**
 * Thrown when a ticket status/priority filter value does not match a known enum constant. Maps to
 * HTTP 422 {@code VALIDATION}. Admin ADD §9 / LLFR-ADMIN-08.1.
 */
public class InvalidTicketStatusException extends RuntimeException {

  public InvalidTicketStatusException(String message) {
    super(message);
  }
}
