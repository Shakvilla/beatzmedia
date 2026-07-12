package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import org.shakvilla.beatzmedia.admin.application.port.out.ComplianceRequestRepository;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceStatus;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;

/**
 * JPA implementation of {@link ComplianceRequestRepository} (admin ADD §5.2 / §7, WU-ADM-8). Reads/
 * writes only this module's V965 table ({@code compliance_request}); no cross-module joins.
 * Transaction boundary = the calling application service.
 */
@ApplicationScoped
public class JpaComplianceRequestRepository implements ComplianceRequestRepository {

  private final EntityManager em;

  @Inject
  public JpaComplianceRequestRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<ComplianceRequest> list(ComplianceType type) {
    String jpql = "SELECT c FROM ComplianceRequestEntity c";
    if (type != null) {
      jpql += " WHERE c.type = :type";
    }
    jpql += " ORDER BY c.createdAt DESC";
    TypedQuery<ComplianceRequestEntity> query =
        em.createQuery(jpql, ComplianceRequestEntity.class);
    if (type != null) {
      query.setParameter("type", type.wireValue());
    }
    return query.getResultList().stream().map(JpaComplianceRequestRepository::toDomain).toList();
  }

  @Override
  public Optional<ComplianceRequest> findById(String requestId) {
    return Optional.ofNullable(em.find(ComplianceRequestEntity.class, requestId))
        .map(JpaComplianceRequestRepository::toDomain);
  }

  @Override
  public void save(ComplianceRequest request) {
    ComplianceRequestEntity entity = em.find(ComplianceRequestEntity.class, request.getId());
    if (entity == null) {
      entity = new ComplianceRequestEntity();
      entity.id = request.getId();
      entity.type = request.getType().wireValue();
      entity.subjectRef = request.getSubjectRef();
      entity.detail = request.getDetail();
      entity.dueAt = request.getDueAt();
      entity.createdAt = request.getCreatedAt();
      entity.status = request.getStatus().wireValue();
      em.persist(entity);
    } else {
      // Only the status is mutable after creation (start/complete).
      entity.status = request.getStatus().wireValue();
      em.merge(entity);
    }
  }

  private static ComplianceRequest toDomain(ComplianceRequestEntity e) {
    return new ComplianceRequest(
        e.id,
        ComplianceType.fromWireValue(e.type),
        e.subjectRef,
        e.detail,
        e.dueAt,
        ComplianceStatus.fromWireValue(e.status),
        e.createdAt);
  }
}
