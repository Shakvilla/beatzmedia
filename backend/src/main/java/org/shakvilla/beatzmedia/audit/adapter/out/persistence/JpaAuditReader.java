package org.shakvilla.beatzmedia.audit.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA query adapter for reading the append-only audit log. Implements {@link AuditReader} used by
 * {@link org.shakvilla.beatzmedia.audit.application.service.ListAuditLogService}. Audit ADD §5.2.
 *
 * <p>Queries are ordered by {@code occurred_at DESC}. Filters: {@code type} (exact), {@code actor}
 * (LIKE search on actor_id and actor_name), {@code q} (LIKE search on action, target_type,
 * target_id). Uses the indexes created in V941.
 */
@ApplicationScoped
public class JpaAuditReader implements AuditReader {

  private final EntityManager em;

  @Inject
  public JpaAuditReader(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<AuditEntry> query(AuditFilter filter, PageRequest page) {
    String baseJpql = buildBaseJpql(filter);

    // Count query
    TypedQuery<Long> countQuery = em.createQuery(
        "SELECT COUNT(a) " + baseJpql, Long.class);
    applyParams(countQuery, filter);
    long total = countQuery.getSingleResult();

    if (total == 0) {
      return Page.empty(page.page(), page.size());
    }

    // Data query
    TypedQuery<AuditEntryEntity> dataQuery = em.createQuery(
        "SELECT a " + baseJpql + " ORDER BY a.occurredAt DESC, a.id DESC", AuditEntryEntity.class);
    applyParams(dataQuery, filter);
    dataQuery.setFirstResult(page.offset());
    dataQuery.setMaxResults(page.size());

    List<AuditEntry> items = dataQuery.getResultList().stream()
        .map(this::toDomain)
        .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  /** Builds the FROM + WHERE clause shared by count and data queries. */
  private String buildBaseJpql(AuditFilter filter) {
    StringBuilder sb = new StringBuilder("FROM AuditEntryEntity a WHERE 1=1");
    if (filter.type() != null) {
      sb.append(" AND a.type = :type");
    }
    if (filter.actor() != null && !filter.actor().isBlank()) {
      sb.append(" AND (LOWER(a.actorId) LIKE :actor OR LOWER(a.actorName) LIKE :actor)");
    }
    if (filter.q() != null && !filter.q().isBlank()) {
      sb.append(
          " AND (LOWER(a.action) LIKE :q OR LOWER(a.targetType) LIKE :q OR"
              + " LOWER(a.targetId) LIKE :q)");
    }
    if (filter.targetId() != null && !filter.targetId().isBlank()) {
      sb.append(" AND a.targetId = :targetId");
    }
    return sb.toString();
  }

  private <T> void applyParams(TypedQuery<T> q, AuditFilter filter) {
    if (filter.type() != null) {
      q.setParameter("type", filter.type().name());
    }
    if (filter.actor() != null && !filter.actor().isBlank()) {
      q.setParameter("actor", "%" + filter.actor().toLowerCase(java.util.Locale.ROOT) + "%");
    }
    if (filter.q() != null && !filter.q().isBlank()) {
      q.setParameter("q", "%" + filter.q().toLowerCase(java.util.Locale.ROOT) + "%");
    }
    if (filter.targetId() != null && !filter.targetId().isBlank()) {
      q.setParameter("targetId", filter.targetId());
    }
  }

  private AuditEntry toDomain(AuditEntryEntity e) {
    return new AuditEntry(
        e.id,
        e.actorId,
        e.actorName,
        e.action,
        e.targetType,
        e.targetId,
        AuditType.valueOf(e.type),
        e.reason,
        e.occurredAt);
  }
}
