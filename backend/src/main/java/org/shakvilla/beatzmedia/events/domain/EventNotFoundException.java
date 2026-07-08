package org.shakvilla.beatzmedia.events.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a requested event does not exist. Maps to 404 {@code NOT_FOUND}. Events ADD §5.1 /
 * §9.
 */
public class EventNotFoundException extends DomainException {

  public EventNotFoundException(String eventId) {
    super(ErrorCode.NOT_FOUND, "Event not found: " + eventId);
  }
}
