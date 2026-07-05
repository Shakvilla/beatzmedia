package org.shakvilla.beatzmedia.notifications.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.notifications.application.port.out.NotificationRepository;
import org.shakvilla.beatzmedia.notifications.domain.Notification;
import org.shakvilla.beatzmedia.notifications.domain.NotificationId;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * JPA implementation of {@link NotificationRepository}. Owns only the {@code notification} table
 * (WU-NOT-1 scope); {@code delivery_attempt} is added in WU-NOT-2. Notifications ADD §5.2.
 */
@ApplicationScoped
public class JpaNotificationRepository implements NotificationRepository {

  private final EntityManager em;

  @Inject
  public JpaNotificationRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public Notification save(Notification notification) {
    NotificationEntity incoming = NotificationEntityMapper.toEntity(notification);
    NotificationEntity existing = em.find(NotificationEntity.class, incoming.id);
    try {
      if (existing == null) {
        em.persist(incoming);
        em.flush();
        return NotificationEntityMapper.toDomain(incoming);
      }
      existing.title = incoming.title;
      existing.body = incoming.body;
      existing.toRoute = incoming.toRoute;
      existing.read = incoming.read;
      existing.readAt = incoming.readAt;
      em.flush();
      return NotificationEntityMapper.toDomain(existing);
    } catch (PersistenceException e) {
      // INV-N4 concurrency guard: a racing insert for the SAME dedupeKey violates the unique
      // partial index (uq_notification_dedupe). Rather than surface a 500 to a benign replay, fall
      // back to the row the winning concurrent write already created.
      if (incoming.dedupeKey != null) {
        Optional<Notification> winner = findByDedupeKey(incoming.dedupeKey);
        if (winner.isPresent()) {
          return winner.get();
        }
      }
      throw e;
    }
  }

  @Override
  public Optional<Notification> findById(NotificationId id) {
    NotificationEntity e = em.find(NotificationEntity.class, id.value());
    return Optional.ofNullable(e).map(NotificationEntityMapper::toDomain);
  }

  @Override
  public Page<Notification> findByRecipient(AccountId recipient, PageRequest page) {
    long total =
        em.createQuery(
                "SELECT count(e) FROM NotificationEntity e WHERE e.recipientId = :rid",
                Long.class)
            .setParameter("rid", recipient.value())
            .getSingleResult();

    List<NotificationEntity> rows =
        em.createQuery(
                "SELECT e FROM NotificationEntity e WHERE e.recipientId = :rid"
                    + " ORDER BY e.createdAt DESC, e.id DESC",
                NotificationEntity.class)
            .setParameter("rid", recipient.value())
            .setFirstResult(page.offset())
            .setMaxResults(page.size())
            .getResultList();

    List<Notification> items = rows.stream().map(NotificationEntityMapper::toDomain).toList();
    return Page.of(items, page.page(), page.size(), total);
  }

  @Override
  public long countUnread(AccountId recipient) {
    return em.createQuery(
            "SELECT count(e) FROM NotificationEntity e"
                + " WHERE e.recipientId = :rid AND e.read = false",
            Long.class)
        .setParameter("rid", recipient.value())
        .getSingleResult();
  }

  @Override
  public int markAllReadForRecipient(AccountId recipient, Instant readAt) {
    return em.createQuery(
            "UPDATE NotificationEntity e SET e.read = true, e.readAt = :readAt"
                + " WHERE e.recipientId = :rid AND e.read = false")
        .setParameter("readAt", readAt)
        .setParameter("rid", recipient.value())
        .executeUpdate();
  }

  @Override
  public boolean existsByDedupeKey(String dedupeKey) {
    Long count =
        em.createQuery(
                "SELECT count(e) FROM NotificationEntity e WHERE e.dedupeKey = :key", Long.class)
            .setParameter("key", dedupeKey)
            .getSingleResult();
    return count != null && count > 0;
  }

  @Override
  public Optional<Notification> findByDedupeKey(String dedupeKey) {
    List<NotificationEntity> results =
        em.createQuery(
                "SELECT e FROM NotificationEntity e WHERE e.dedupeKey = :key",
                NotificationEntity.class)
            .setParameter("key", dedupeKey)
            .getResultList();
    return results.stream().findFirst().map(NotificationEntityMapper::toDomain);
  }
}
