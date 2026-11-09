package org.shakvilla.beatzmedia.platform.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.in.InspectDurableEvents;
import org.shakvilla.beatzmedia.platform.application.port.in.ReplayDurableEvent;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventCompatibility;
import org.shakvilla.beatzmedia.platform.application.port.out.DurableEventStore;
import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.DurableEventFilter;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;
import org.shakvilla.beatzmedia.platform.domain.DurableEventStatus;
import org.shakvilla.beatzmedia.platform.domain.DurableQueueHealth;
import org.shakvilla.beatzmedia.platform.domain.Page;

@ApplicationScoped
public class DurableEventOperationsService implements InspectDurableEvents, ReplayDurableEvent {

  private final DurableEventStore store;
  private final DurableEventCompatibility compatibility;
  private final Clock clock;

  @Inject
  public DurableEventOperationsService(
      DurableEventStore store, DurableEventCompatibility compatibility, Clock clock) {
    this.store = store;
    this.compatibility = compatibility;
    this.clock = clock;
  }

  @Override
  public Page<DurableEventRecord> search(DurableEventFilter filter) {
    return store.search(filter);
  }

  @Override
  public DurableQueueHealth health() {
    return store.health(clock.now());
  }

  @Override
  @Transactional
  public DurableEventRecord replay(String eventId, String actorId) {
    DurableEventRecord event = store.find(eventId);
    if (event.status() != DurableEventStatus.FAILED) {
      throw new ConflictException("Only failed durable events may be replayed");
    }
    if (!compatibility.supports(event.eventType(), event.schemaVersion())) {
      throw new ConflictException(
          "No compatible handler for " + event.eventType() + " version " + event.schemaVersion());
    }
    return store.replay(eventId, actorId, clock.now());
  }
}
