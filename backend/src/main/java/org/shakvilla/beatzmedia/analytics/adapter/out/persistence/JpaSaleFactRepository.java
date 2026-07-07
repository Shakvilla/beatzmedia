package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.SaleFactRepository;
import org.shakvilla.beatzmedia.analytics.domain.SaleFact;

/**
 * JPA implementation of {@link SaleFactRepository} over {@code analytics_sale_fact} (V949).
 * Analytics ADD §5.2.
 */
@ApplicationScoped
public class JpaSaleFactRepository implements SaleFactRepository {

  private final EntityManager em;

  @Inject
  public JpaSaleFactRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  public void append(SaleFact fact) {
    SaleFactEntity entity = new SaleFactEntity();
    entity.id = fact.id();
    entity.artistId = fact.artistId();
    entity.grossMinor = fact.grossMinor();
    entity.currency = fact.currency();
    entity.occurredAt = fact.occurredAt();
    entity.processed = fact.processed();
    em.persist(entity);
  }

  @Override
  public List<SaleFact> findUnprocessed() {
    List<SaleFactEntity> rows =
        em.createQuery(
                "SELECT f FROM SaleFactEntity f WHERE f.processed = false ORDER BY f.occurredAt ASC",
                SaleFactEntity.class)
            .getResultList();
    return rows.stream().map(JpaSaleFactRepository::toDomain).toList();
  }

  @Override
  public void markProcessed(List<String> factIds) {
    if (factIds.isEmpty()) {
      return;
    }
    em.createQuery("UPDATE SaleFactEntity f SET f.processed = true WHERE f.id IN :ids")
        .setParameter("ids", factIds)
        .executeUpdate();
  }

  private static SaleFact toDomain(SaleFactEntity e) {
    return new SaleFact(e.id, e.artistId, e.grossMinor, e.currency, e.occurredAt, e.processed);
  }
}
