package org.shakvilla.beatzmedia.events.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.events.application.port.in.EventFilter;
import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.port.in.ListEvents;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.Event;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Application service for LLFR-EVENTS-01.1 (browse events). Events ADD §4.1. */
@ApplicationScoped
@Transactional
public class ListEventsService implements ListEvents {

  private final EventRepository repository;

  @Inject
  public ListEventsService(EventRepository repository) {
    this.repository = repository;
  }

  @Override
  public Page<EventView> list(EventFilter filter, PageRequest page, Optional<AccountId> caller) {
    Page<Event> events = repository.find(filter, page);
    return new Page<>(
        events.items().stream().map(EventMapper::toView).toList(),
        events.page(),
        events.size(),
        events.total());
  }
}
