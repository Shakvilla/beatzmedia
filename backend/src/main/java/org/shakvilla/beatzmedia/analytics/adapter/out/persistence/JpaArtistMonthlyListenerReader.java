package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.catalog.application.port.out.ArtistMonthlyListenerReader;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** Reads retained play facts, including facts already consumed by the rollup job. */
@ApplicationScoped
public class JpaArtistMonthlyListenerReader implements ArtistMonthlyListenerReader {

  private final EntityManager em;

  @Inject
  public JpaArtistMonthlyListenerReader(EntityManager em) {
    this.em = em;
  }

  @Override
  public long count(ArtistId artistId, Instant since, Instant asOf) {
    return ((Number) em.createNativeQuery(
            "SELECT count(DISTINCT account_id) FROM analytics_play_fact"
                + " WHERE artist_id = :artistId AND account_id IS NOT NULL"
                + " AND occurred_at >= :since AND occurred_at <= :asOf")
        .setParameter("artistId", artistId.value())
        .setParameter("since", since)
        .setParameter("asOf", asOf)
        .getSingleResult()).longValue();
  }
}
