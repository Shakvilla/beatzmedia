package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** List events for the Fan surface with optional city/category filters. LLFR-EVENTS-01.1. */
public interface ListEvents {

  Page<EventView> list(EventFilter filter, PageRequest page, Optional<AccountId> caller);
}
