package org.shakvilla.beatzmedia.audit.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;

/**
 * Thin JPA adapter that INSERTs audit entries into the {@code audit_entry} table. Append-only: no
 * UPDATE or DELETE paths exist. Runs within the calling use-case's transaction (INV-10). Audit ADD
 * §5.2 / migration V941 (table) + V942 (actor_name).
 */
@ApplicationScoped
public class JpaAuditWriter implements AuditWriter {

  private final EntityManager em;

  @Inject
  public JpaAuditWriter(EntityManager em) {
    this.em = em;
  }

  @Override
  public void append(AuditEntry entry) {
    AuditEntryEntity entity = new AuditEntryEntity();
    entity.id = entry.getId();
    entity.actorId = entry.getActor();
    entity.actorName = entry.getActorName();
    entity.action = entry.getAction();
    entity.targetType = entry.getTargetType();
    entity.targetId = entry.getTargetId();
    entity.type = entry.getType().name();
    entity.reason = entry.getReason();
    entity.occurredAt = entry.getOccurredAt();
    em.persist(entity);
  }
}
