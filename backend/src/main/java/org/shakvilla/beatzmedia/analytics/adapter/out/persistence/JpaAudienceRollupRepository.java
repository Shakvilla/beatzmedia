package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.AudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * JPA implementation of {@link AudienceRollupRepository} over {@code audience_rollup} (V949).
 * Upsert is keyed on the unique {@code (artist_id, bucket, grain)} constraint (ADD §4.1/§5.2).
 */
@ApplicationScoped
public class JpaAudienceRollupRepository implements AudienceRollupRepository {

  private final EntityManager em;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public JpaAudienceRollupRepository(EntityManager em, IdGenerator ids, Clock clock) {
    this.em = em;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public void upsert(AudienceRollup rollup) {
    em.createNativeQuery(
            "INSERT INTO audience_rollup "
                + "(id, artist_id, bucket, grain, plays, followers_gained, unique_listeners, "
                + " completion_pct, computed_at) "
                + "VALUES (:id, :artist, :bucket, :grain, :plays, :followers, :unique, :completion, :now) "
                + "ON CONFLICT (artist_id, bucket, grain) DO UPDATE SET "
                + " plays = EXCLUDED.plays, "
                + " followers_gained = EXCLUDED.followers_gained, "
                + " unique_listeners = EXCLUDED.unique_listeners, "
                + " completion_pct = EXCLUDED.completion_pct, "
                + " computed_at = EXCLUDED.computed_at")
        .setParameter("id", ids.newId())
        .setParameter("artist", rollup.artistId().value())
        .setParameter("bucket", rollup.bucket().bucket())
        .setParameter("grain", rollup.bucket().grain().name())
        .setParameter("plays", rollup.plays())
        .setParameter("followers", rollup.followersGained())
        .setParameter("unique", rollup.uniqueListeners())
        .setParameter("completion", rollup.completionPct())
        .setParameter("now", clock.now())
        .executeUpdate();
  }

  @Override
  public Optional<AudienceRollup> find(ArtistId artistId, LocalDate bucket, Grain grain) {
    List<AudienceRollupEntity> rows =
        em.createQuery(
                "SELECT r FROM AudienceRollupEntity r "
                    + "WHERE r.artistId = :artist AND r.bucket = :bucket AND r.grain = :grain",
                AudienceRollupEntity.class)
            .setParameter("artist", artistId.value())
            .setParameter("bucket", bucket)
            .setParameter("grain", grain.name())
            .setMaxResults(1)
            .getResultList();
    return rows.stream().findFirst().map(JpaAudienceRollupRepository::toDomain);
  }

  @Override
  public List<AudienceRollup> findRange(ArtistId artistId, Grain grain, LocalDate from, LocalDate to) {
    List<AudienceRollupEntity> rows =
        em.createQuery(
                "SELECT r FROM AudienceRollupEntity r WHERE r.artistId = :artist AND r.grain = :grain "
                    + "AND r.bucket BETWEEN :from AND :to ORDER BY r.bucket ASC",
                AudienceRollupEntity.class)
            .setParameter("artist", artistId.value())
            .setParameter("grain", grain.name())
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();
    return rows.stream().map(JpaAudienceRollupRepository::toDomain).toList();
  }

  private static AudienceRollup toDomain(AudienceRollupEntity e) {
    return new AudienceRollup(
        new ArtistId(e.artistId),
        new RollupBucket(e.bucket, Grain.valueOf(e.grain)),
        e.plays,
        e.followersGained,
        e.uniqueListeners,
        e.completionPct);
  }
}
