package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.PlayFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.PlayFact;

/**
 * JPA implementation of {@link PlayFactRepository} over {@code analytics_play_fact} (V949).
 * Analytics ADD §5.2.
 */
@ApplicationScoped
public class JpaPlayFactRepository implements PlayFactRepository {

  private final EntityManager em;

  @Inject
  public JpaPlayFactRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void append(PlayFact fact) {
    PlayFactEntity entity = new PlayFactEntity();
    entity.id = fact.id();
    entity.artistId = fact.artistId();
    entity.accountId = fact.accountId();
    entity.occurredAt = fact.occurredAt();
    entity.processed = fact.processed();
    em.persist(entity);
  }

  @Override
  public List<PlayFact> findUnprocessed() {
    List<PlayFactEntity> rows =
        em.createQuery(
                "SELECT f FROM PlayFactEntity f WHERE f.processed = false ORDER BY f.occurredAt ASC",
                PlayFactEntity.class)
            .getResultList();
    return rows.stream().map(JpaPlayFactRepository::toDomain).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    if (factIds.isEmpty()) {
      return;
    }
    em.createQuery("UPDATE PlayFactEntity f SET f.processed = true WHERE f.id IN :ids")
        .setParameter("ids", factIds)
        .executeUpdate();
  }

  private static PlayFact toDomain(PlayFactEntity e) {
    return new PlayFact(e.id, e.artistId, e.accountId, e.occurredAt, e.processed);
  }
}
