package org.shakvilla.beatzmedia.analytics.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.shakvilla.beatzmedia.analytics.application.port.out.SalesRollupRepository;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.RollupBucket;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * JPA implementation of {@link SalesRollupRepository} over {@code sales_rollup} (V949). Upsert is
 * keyed on the unique {@code (artist_id, bucket, grain)} constraint so re-running a rollup window
 * is idempotent (ADD §4.1/§5.2) — {@code royalty_minor} is always persisted as {@code 0} (OQ-4).
 */
@ApplicationScoped
public class JpaSalesRollupRepository implements SalesRollupRepository {

  private final EntityManager em;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public JpaSalesRollupRepository(EntityManager em, IdGenerator ids, Clock clock) {
    this.em = em;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public void upsert(SalesRollup rollup) {
    em.createNativeQuery(
            "INSERT INTO sales_rollup "
                + "(id, artist_id, bucket, grain, sales_minor, tips_minor, royalty_minor, units, computed_at) "
                + "VALUES (:id, :artist, :bucket, :grain, :sales, :tips, 0, :units, :now) "
                + "ON CONFLICT (artist_id, bucket, grain) DO UPDATE SET "
                + " sales_minor = EXCLUDED.sales_minor, "
                + " tips_minor = EXCLUDED.tips_minor, "
                + " royalty_minor = 0, "
                + " units = EXCLUDED.units, "
                + " computed_at = EXCLUDED.computed_at")
        .setParameter("id", ids.newId())
        .setParameter("artist", rollup.artistId().value())
        .setParameter("bucket", rollup.bucket().bucket())
        .setParameter("grain", rollup.bucket().grain().name())
        .setParameter("sales", rollup.salesMinor())
        .setParameter("tips", rollup.tipsMinor())
        .setParameter("units", rollup.units())
        .setParameter("now", clock.now())
        .executeUpdate();
  }

  /**
   * Reads the current row with a NATIVE scalar query, NOT a JPA entity query. This is deliberate:
   * the rollup jobs read-modify-write a bucket once per fact via {@link #find} + {@link #upsert},
   * and {@link #upsert} writes through native SQL. A JPA entity {@code find} would, on the 2nd read
   * of a bucket within the same transaction, return the stale first-level-cached instance (Hibernate
   * guarantees session identity and never overwrites managed state from a native write) — silently
   * undercounting when a bucket has 3+ facts in one window. A scalar native read always sees the
   * committed-so-far value.
   */
  @Override
  public Optional<SalesRollup> find(ArtistId artistId, LocalDate bucket, Grain grain) {
    List<?> rows =
        em.createNativeQuery(
                "SELECT sales_minor, tips_minor, units FROM sales_rollup "
                    + "WHERE artist_id = :artist AND bucket = :bucket AND grain = :grain")
            .setParameter("artist", artistId.value())
            .setParameter("bucket", bucket)
            .setParameter("grain", grain.name())
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object[] r = (Object[]) rows.get(0);
    return Optional.of(
        new SalesRollup(
            artistId,
            new RollupBucket(bucket, grain),
            ((Number) r[0]).longValue(),
            ((Number) r[1]).longValue(),
            0L,
            ((Number) r[2]).intValue()));
  }

  @Override
  public List<SalesRollup> findRange(ArtistId artistId, Grain grain, LocalDate from, LocalDate to) {
    List<SalesRollupEntity> rows =
        em.createQuery(
                "SELECT r FROM SalesRollupEntity r WHERE r.artistId = :artist AND r.grain = :grain "
                    + "AND r.bucket BETWEEN :from AND :to ORDER BY r.bucket ASC",
                SalesRollupEntity.class)
            .setParameter("artist", artistId.value())
            .setParameter("grain", grain.name())
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();
    return rows.stream().map(JpaSalesRollupRepository::toDomain).toList();
  }

  private static SalesRollup toDomain(SalesRollupEntity e) {
    return new SalesRollup(
        new ArtistId(e.artistId),
        new RollupBucket(e.bucket, Grain.valueOf(e.grain)),
        e.salesMinor,
        e.tipsMinor,
        0L,
        e.units);
  }
}
