package org.shakvilla.beatzmedia.events.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.events.domain.EventCategory;

/** Optional browse filters for {@link ListEvents}. Events ADD §4.1 / §5.1. */
public record EventFilter(Optional<String> city, Optional<EventCategory> category) {

  public static final EventFilter NONE = new EventFilter(Optional.empty(), Optional.empty());
}
