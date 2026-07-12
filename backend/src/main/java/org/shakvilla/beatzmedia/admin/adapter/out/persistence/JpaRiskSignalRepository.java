package org.shakvilla.beatzmedia.admin.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.admin.application.port.out.RiskSignalRepository;
import org.shakvilla.beatzmedia.admin.domain.RiskLevel;
import org.shakvilla.beatzmedia.admin.domain.RiskSignal;
import org.shakvilla.beatzmedia.admin.domain.RiskStatus;

/**
 * JPA implementation of {@link RiskSignalRepository} (admin ADD §5.2 / §7, WU-ADM-6). Reads/writes
 * only this module's V964 table ({@code risk_signal}); no cross-module joins. Transaction boundary =
 * the calling application service.
 */
@ApplicationScoped
public class JpaRiskSignalRepository implements RiskSignalRepository {

  private final EntityManager em;

  @Inject
  public JpaRiskSignalRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public List<RiskSignal> list() {
    return em
        .createQuery(
            "SELECT r FROM RiskSignalEntity r ORDER BY r.detectedAt DESC", RiskSignalEntity.class)
        .getResultList()
        .stream()
        .map(JpaRiskSignalRepository::toDomain)
        .toList();
  }

  @Override
  public Optional<RiskSignal> findById(String signalId) {
    return Optional.ofNullable(em.find(RiskSignalEntity.class, signalId))
        .map(JpaRiskSignalRepository::toDomain);
  }

  @Override
  public void save(RiskSignal signal) {
    RiskSignalEntity entity = em.find(RiskSignalEntity.class, signal.getId());
    if (entity == null) {
      entity = new RiskSignalEntity();
      entity.id = signal.getId();
      entity.subjectRef = signal.getSubjectRef();
      entity.type = signal.getType();
      entity.detail = signal.getDetail();
      entity.level = signal.getLevel().wireValue();
      entity.detectedAt = signal.getDetectedAt();
      entity.status = signal.getStatus().wireValue();
      em.persist(entity);
    } else {
      // Only the status is mutable after creation (review/clear/ban).
      entity.status = signal.getStatus().wireValue();
      em.merge(entity);
    }
  }

  @Override
  public long countOpen() {
    return em.createQuery(
            "SELECT COUNT(r) FROM RiskSignalEntity r WHERE r.status = :st", Long.class)
        .setParameter("st", RiskStatus.OPEN.wireValue())
        .getSingleResult();
  }

  private static RiskSignal toDomain(RiskSignalEntity e) {
    return new RiskSignal(
        e.id,
        e.subjectRef,
        e.type,
        e.detail,
        RiskLevel.fromWireValue(e.level),
        RiskStatus.fromWireValue(e.status),
        e.detectedAt);
  }
}
