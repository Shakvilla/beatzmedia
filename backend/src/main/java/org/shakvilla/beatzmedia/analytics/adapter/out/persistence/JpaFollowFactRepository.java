package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.FollowFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;

/**
 * JPA implementation of {@link FollowFactRepository} over {@code analytics_follow_fact} (V949).
 * Analytics ADD §5.2.
 */
@ApplicationScoped
public class JpaFollowFactRepository implements FollowFactRepository {

  private final EntityManager em;

  @Inject
  public JpaFollowFactRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void append(FollowFact fact) {
    FollowFactEntity entity = new FollowFactEntity();
    entity.id = fact.id();
    entity.artistId = fact.artistId();
    entity.occurredAt = fact.occurredAt();
    entity.processed = fact.processed();
    em.persist(entity);
  }

  @Override
  public List<FollowFact> findUnprocessed() {
    List<FollowFactEntity> rows =
        em.createQuery(
                "SELECT f FROM FollowFactEntity f WHERE f.processed = false ORDER BY f.occurredAt ASC",
                FollowFactEntity.class)
            .getResultList();
    return rows.stream().map(JpaFollowFactRepository::toDomain).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    if (factIds.isEmpty()) {
      return;
    }
    em.createQuery("UPDATE FollowFactEntity f SET f.processed = true WHERE f.id IN :ids")
        .setParameter("ids", factIds)
        .executeUpdate();
  }

  private static FollowFact toDomain(FollowFactEntity e) {
    return new FollowFact(e.id, e.artistId, e.occurredAt, e.processed);
  }
}
