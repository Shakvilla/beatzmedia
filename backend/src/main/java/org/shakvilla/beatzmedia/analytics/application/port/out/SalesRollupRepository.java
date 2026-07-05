package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.time.LocalDate;
import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.analytics.domain.SalesRollup;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Output port over the {@code sales_rollup} table. Upsert is keyed by the unique
 * {@code (artist_id, bucket, grain)} constraint (ADD §7) so re-running a window is idempotent.
 * Analytics ADD §4.1 / §5.2.
 */
public interface SalesRollupRepository {

  /** Insert or replace the row for {@code rollup}'s key — NOT an additive merge (ADD §5.2). */
  void upsert(SalesRollup rollup);

  /** Fetch the current row for a key, if any (used by the job to fold new facts in). */
  java.util.Optional<SalesRollup> find(ArtistId artistId, LocalDate bucket, Grain grain);

  /** All rows for one artist at one grain within {@code [from, to]} inclusive, bucket ascending. */
  List<SalesRollup> findRange(ArtistId artistId, Grain grain, LocalDate from, LocalDate to);
}
