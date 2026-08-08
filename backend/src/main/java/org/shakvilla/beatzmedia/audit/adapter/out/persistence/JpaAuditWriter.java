package org.shakvilla.beatzmedia.audit.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.audit.application.port.out.ActorDirectory;
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
  private final ActorDirectory actors;

  @Inject
  public JpaAuditWriter(EntityManager em, ActorDirectory actors) {
    this.em = em;
    this.actors = actors;
  }

  @Override
  public void append(AuditEntry entry) {
    AuditEntryEntity entity = new AuditEntryEntity();
    entity.id = entry.getId();
    entity.actorId = entry.getActor();
    entity.actorName = resolveActorName(entry);
    entity.action = entry.getAction();
    entity.targetType = entry.getTargetType();
    entity.targetId = entry.getTargetId();
    entity.type = entry.getType().name();
    entity.reason = entry.getReason();
    entity.occurredAt = entry.getOccurredAt();
    em.persist(entity);
  }

  /**
   * Fills in the actor's display name when the caller did not supply one.
   *
   * <p>Every audit row the application wrote had a null {@code actor_name}, so the audit log
   * rendered raw UUIDs — {@code 019fe17e-…· SUBMIT_RELEASE · Release:019fe1af-…} — on a page whose
   * own subtitle promises "every privileged admin action, with actor and time". The chain always
   * supported the name: the column, the entity, the domain field and the read query were all there.
   * Only the 48 call sites never passed it, each using the constructor overload that defaults it to
   * null.
   *
   * <p>Resolving here rather than at those 48 sites keeps the {@code AuditWriter} signature
   * unchanged and means a new audited action cannot forget to do it. A caller that <em>does</em>
   * supply a name — the {@code @Audited} interceptor takes one from the JWT — is left alone.
   */
  private String resolveActorName(AuditEntry entry) {
    if (entry.getActorName() != null && !entry.getActorName().isBlank()) {
      return entry.getActorName();
    }
    return actors.displayName(entry.getActor()).orElse(null);
  }
}
