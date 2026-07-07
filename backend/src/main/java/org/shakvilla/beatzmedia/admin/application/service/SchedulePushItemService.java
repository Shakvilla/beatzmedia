package org.shakvilla.beatzmedia.admin.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.SchedulePushItem;
import org.shakvilla.beatzmedia.admin.application.port.out.PushItemRepository;
import org.shakvilla.beatzmedia.admin.domain.PushItem;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-06.1 (schedule push). Auth: editor, super-admin (enforced by
 * the inbound {@code @RolesAllowed}). Blank-field validation happens in the {@link PushItem}
 * constructor (422 before any persistence). Appends exactly one {@link AuditEntry} (INV-10) of
 * {@code type=editorial}. Admin ADD §4.1 / §9.
 */
@ApplicationScoped
public class SchedulePushItemService implements SchedulePushItem {

  private final PushItemRepository pushItems;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public SchedulePushItemService(
      PushItemRepository pushItems, AuditWriter auditWriter, IdGenerator idGenerator, Clock clock) {
    this.pushItems = pushItems;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PushItem schedule(String actorId, PushItemInput input) {
    PushItem item = new PushItem(
        idGenerator.newId(),
        input.day(),
        input.timeLabel(),
        input.title(),
        input.audience(),
        input.scheduledAt());

    PushItem saved = pushItems.save(item);

    Instant now = clock.now();
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Scheduled push",
        "PushItem",
        saved.getId(),
        AuditType.EDITORIAL,
        null,
        now));

    return saved;
  }
}
