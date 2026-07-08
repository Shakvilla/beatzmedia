package org.shakvilla.beatzmedia.events.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown by the internal {@code IssueTicket} port when {@code commerce} references an unknown
 * ticket tier id. Never REST-exposed (the port is in-process only). Maps to {@code NOT_FOUND}.
 * Events ADD §4.1.
 */
public class TicketTierNotFoundException extends DomainException {

  public TicketTierNotFoundException(String tierId) {
    super(ErrorCode.NOT_FOUND, "Ticket tier not found: " + tierId);
  }
}
