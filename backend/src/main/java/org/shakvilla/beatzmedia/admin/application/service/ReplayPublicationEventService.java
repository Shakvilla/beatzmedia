package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ReplayPublicationEvent;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.in.ReplayDurableEvent;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.DurableEventRecord;

@ApplicationScoped
public class ReplayPublicationEventService implements ReplayPublicationEvent {

  private final ReplayDurableEvent replayDurableEvent;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public ReplayPublicationEventService(
      ReplayDurableEvent replayDurableEvent,
      AuditWriter auditWriter,
      IdGenerator ids,
      Clock clock) {
    this.replayDurableEvent = replayDurableEvent;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public DurableEventRecord replay(String actorId, String eventId, String reason) {
    DurableEventRecord replayed = replayDurableEvent.replay(eventId, actorId);
    auditWriter.append(new AuditEntry(
        ids.newId(),
        actorId,
        "Replayed publication event",
        "PlatformOutboxEvent",
        eventId,
        AuditType.SETTINGS,
        reason,
        clock.now()));
    return replayed;
  }
}
