package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Single event detail incl. tiers with live availability. LLFR-EVENTS-01.2. */
public interface GetEvent {

  EventView get(EventId eventId, Optional<AccountId> caller);
}
