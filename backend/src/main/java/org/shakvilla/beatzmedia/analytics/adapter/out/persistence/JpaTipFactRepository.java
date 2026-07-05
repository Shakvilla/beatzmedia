package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.TipFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.TipFact;

/**
 * JPA implementation of {@link TipFactRepository} over {@code analytics_tip_fact} (V949). Analytics
 * ADD §5.2.
 */
@ApplicationScoped
public class JpaTipFactRepository implements TipFactRepository {

  private final EntityManager em;

  @Inject
  public JpaTipFactRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void append(TipFact fact) {
    TipFactEntity entity = new TipFactEntity();
    entity.id = fact.id();
    entity.artistId = fact.artistId();
    entity.creatorShareMinor = fact.creatorShareMinor();
    entity.currency = fact.currency();
    entity.occurredAt = fact.occurredAt();
    entity.processed = fact.processed();
    em.persist(entity);
  }

  @Override
  public List<TipFact> findUnprocessed() {
    List<TipFactEntity> rows =
        em.createQuery(
                "SELECT f FROM TipFactEntity f WHERE f.processed = false ORDER BY f.occurredAt ASC",
                TipFactEntity.class)
            .getResultList();
    return rows.stream().map(JpaTipFactRepository::toDomain).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    if (factIds.isEmpty()) {
      return;
    }
    em.createQuery("UPDATE TipFactEntity f SET f.processed = true WHERE f.id IN :ids")
        .setParameter("ids", factIds)
        .executeUpdate();
  }

  private static TipFact toDomain(TipFactEntity e) {
    return new TipFact(e.id, e.artistId, e.creatorShareMinor, e.currency, e.occurredAt, e.processed);
  }
}
