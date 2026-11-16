package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.notifications.application.port.out.PublicationFanoutRepository;
import org.shakvilla.beatzmedia.notifications.domain.FanoutStatus;
import org.shakvilla.beatzmedia.notifications.domain.PublicationFanoutJob;
import org.shakvilla.beatzmedia.notifications.domain.PublicationKind;

@ApplicationScoped
public class JpaPublicationFanoutRepository implements PublicationFanoutRepository {

  private final EntityManager em;

  @Inject
  public JpaPublicationFanoutRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void enqueue(PublicationFanoutJob job) {
    em.createNativeQuery(
            "INSERT INTO publication_notification_fanout"
                + " (id, publication_kind, content_id, target_kind, target_id, title, body, route,"
                + " cursor, status, retry_count, next_attempt_at, last_error, created_at, updated_at,"
                + " completed_at) VALUES"
                + " (:id, :kind, :contentId, :targetKind, :targetId, :title, :body, :route, :cursor,"
                + " :status, :retryCount, :nextAttemptAt, :lastError, :createdAt, :updatedAt,"
                + " :completedAt) ON CONFLICT (publication_kind, content_id) DO NOTHING")
        .setParameter("id", job.id())
        .setParameter("kind", job.publicationKind().name())
        .setParameter("contentId", job.contentId())
        .setParameter("targetKind", job.targetKind())
        .setParameter("targetId", job.targetId())
        .setParameter("title", job.title())
        .setParameter("body", job.body())
        .setParameter("route", job.route())
        .setParameter("cursor", job.cursor())
        .setParameter("status", job.status().name())
        .setParameter("retryCount", job.retryCount())
        .setParameter("nextAttemptAt", job.nextAttemptAt())
        .setParameter("lastError", job.lastError())
        .setParameter("createdAt", job.createdAt())
        .setParameter("updatedAt", job.updatedAt())
        .setParameter("completedAt", job.completedAt())
        .executeUpdate();
  }

  @Override
  public List<PublicationFanoutJob> findDue(Instant now, int limit) {
    return em.createQuery(
            "SELECT e FROM PublicationFanoutEntity e"
                + " WHERE e.status = :pending AND e.nextAttemptAt <= :now"
                + " ORDER BY e.updatedAt ASC, e.createdAt ASC, e.id ASC",
            PublicationFanoutEntity.class)
        .setParameter("pending", FanoutStatus.pending.name())
        .setParameter("now", now)
        .setMaxResults(limit)
        .getResultList()
        .stream()
        .map(JpaPublicationFanoutRepository::toDomain)
        .toList();
  }

  @Override
  public void advance(String jobId, String nextCursor, Instant now) {
    em.createQuery(
            "UPDATE PublicationFanoutEntity e SET e.cursor = :cursor, e.retryCount = 0,"
                + " e.nextAttemptAt = :now, e.lastError = null, e.updatedAt = :now"
                + " WHERE e.id = :id AND e.status = :pending")
        .setParameter("cursor", nextCursor)
        .setParameter("now", now)
        .setParameter("id", jobId)
        .setParameter("pending", FanoutStatus.pending.name())
        .executeUpdate();
  }

  @Override
  public void complete(String jobId, Instant now) {
    em.createQuery(
            "UPDATE PublicationFanoutEntity e SET e.status = :completed, e.retryCount = 0,"
                + " e.lastError = null, e.updatedAt = :now, e.completedAt = :now"
                + " WHERE e.id = :id AND e.status = :pending")
        .setParameter("completed", FanoutStatus.completed.name())
        .setParameter("pending", FanoutStatus.pending.name())
        .setParameter("now", now)
        .setParameter("id", jobId)
        .executeUpdate();
  }

  @Override
  public void reschedule(
      String jobId, int retryCount, Instant nextAttemptAt, String error, Instant now) {
    em.createQuery(
            "UPDATE PublicationFanoutEntity e SET e.retryCount = :retryCount,"
                + " e.nextAttemptAt = :nextAttemptAt, e.lastError = :error, e.updatedAt = :now"
                + " WHERE e.id = :id AND e.status = :pending")
        .setParameter("retryCount", retryCount)
        .setParameter("nextAttemptAt", nextAttemptAt)
        .setParameter("error", error)
        .setParameter("now", now)
        .setParameter("id", jobId)
        .setParameter("pending", FanoutStatus.pending.name())
        .executeUpdate();
  }

  private static PublicationFanoutJob toDomain(PublicationFanoutEntity entity) {
    return new PublicationFanoutJob(
        entity.id,
        PublicationKind.valueOf(entity.publicationKind),
        entity.contentId,
        entity.targetKind,
        entity.targetId,
        entity.title,
        entity.body,
        entity.route,
        entity.cursor,
        FanoutStatus.valueOf(entity.status),
        entity.retryCount,
        entity.nextAttemptAt,
        entity.lastError,
        entity.createdAt,
        entity.updatedAt,
        entity.completedAt);
  }
}
