package org.shakvilla.beatzmedia.events.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.events.application.port.in.EventView;
import org.shakvilla.beatzmedia.events.application.port.in.GetEvent;
import org.shakvilla.beatzmedia.events.application.port.out.EventRepository;
import org.shakvilla.beatzmedia.events.domain.EventId;
import org.shakvilla.beatzmedia.events.domain.EventNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/** Application service for LLFR-EVENTS-01.2 (event detail). Events ADD §4.1. */
@ApplicationScoped
@Transactional
public class GetEventService implements GetEvent {

  private final EventRepository repository;

  @Inject
  public GetEventService(EventRepository repository) {
    this.repository = repository;
  }

  @Override
  public EventView get(EventId eventId, Optional<AccountId> caller) {
    return repository
        .findById(eventId)
        .map(EventMapper::toView)
        .orElseThrow(() -> new EventNotFoundException(eventId.value()));
  }
}
