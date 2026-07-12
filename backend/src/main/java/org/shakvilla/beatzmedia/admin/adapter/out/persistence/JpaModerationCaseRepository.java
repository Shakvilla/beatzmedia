package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModSeverity;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA implementation of {@link ModerationCaseRepository} (admin ADD §5.2 / §7, WU-ADM-3). Reads/
 * writes only this module's V963 table ({@code moderation_case}); no cross-module joins.
 * Transaction boundary = the calling application service.
 */
@ApplicationScoped
public class JpaModerationCaseRepository implements ModerationCaseRepository {

  private final EntityManager em;

  @Inject
  public JpaModerationCaseRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Page<ModerationCase> list(ModStatus status, ModReason type, PageRequest page) {
    StringBuilder jpql = new StringBuilder("SELECT m FROM ModerationCaseEntity m WHERE 1=1");
    StringBuilder countJpql = new StringBuilder("SELECT COUNT(m) FROM ModerationCaseEntity m WHERE 1=1");
    if (status != null) {
      jpql.append(" AND m.status = :status");
      countJpql.append(" AND m.status = :status");
    }
    if (type != null) {
      jpql.append(" AND m.reason = :reason");
      countJpql.append(" AND m.reason = :reason");
    }
    jpql.append(" ORDER BY m.createdAt DESC");

    TypedQuery<ModerationCaseEntity> query = em.createQuery(jpql.toString(), ModerationCaseEntity.class);
    TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);
    if (status != null) {
      query.setParameter("status", status.wireValue());
      countQuery.setParameter("status", status.wireValue());
    }
    if (type != null) {
      query.setParameter("reason", type.wireValue());
      countQuery.setParameter("reason", type.wireValue());
    }

    long total = countQuery.getSingleResult();
    if (total == 0) {
      return Page.empty(page.page(), page.size());
    }

    List<ModerationCase> items = query.setFirstResult(page.offset())
        .setMaxResults(page.size())
        .getResultList()
        .stream()
        .map(JpaModerationCaseRepository::toDomain)
        .toList();

    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public Optional<ModerationCase> findById(String caseId) {
    ModerationCaseEntity entity = em.find(ModerationCaseEntity.class, caseId);
    return Optional.ofNullable(entity).map(JpaModerationCaseRepository::toDomain);
  }

  @Override
  public void save(ModerationCase moderationCase) {
    ModerationCaseEntity entity = em.find(ModerationCaseEntity.class, moderationCase.getId());
    boolean isNew = entity == null;
    if (isNew) {
      entity = new ModerationCaseEntity();
      entity.id = moderationCase.getId();
      entity.targetRef = moderationCase.getTargetRef();
      entity.reporter = moderationCase.getReporter();
      entity.reason = moderationCase.getReason().wireValue();
      entity.slaDueAt = moderationCase.getSlaDueAt();
      entity.createdAt = moderationCase.getCreatedAt();
    }
    entity.severity = moderationCase.getSeverity().wireValue();
    entity.status = moderationCase.getStatus().wireValue();
    entity.escalated = moderationCase.isEscalated();

    if (isNew) {
      em.persist(entity);
    } else {
      em.merge(entity);
    }
  }

  @Override
  public Summary summary() {
    Object[] row = (Object[]) em.createNativeQuery(
            "SELECT COUNT(*) FILTER (WHERE status = 'open'), "
                + "COUNT(*) FILTER (WHERE is_escalated = true AND status <> 'resolved') "
                + "FROM moderation_case")
        .getSingleResult();
    return new Summary(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
  }

  private static ModerationCase toDomain(ModerationCaseEntity e) {
    return new ModerationCase(
        e.id,
        e.targetRef,
        e.reporter,
        ModReason.fromWireValue(e.reason),
        ModSeverity.fromWireValue(e.severity),
        ModStatus.fromWireValue(e.status),
        e.slaDueAt,
        e.escalated,
        e.createdAt);
  }
}
