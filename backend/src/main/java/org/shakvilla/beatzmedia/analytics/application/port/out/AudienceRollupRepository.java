package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Output port over the {@code audience_rollup} table. Upsert is keyed by the unique
 * {@code (artist_id, bucket, grain)} constraint (ADD §7). Analytics ADD §4.1 / §5.2.
 */
public interface AudienceRollupRepository {

  void upsert(AudienceRollup rollup);

  Optional<AudienceRollup> find(ArtistId artistId, LocalDate bucket, Grain grain);

  List<AudienceRollup> findRange(ArtistId artistId, Grain grain, LocalDate from, LocalDate to);
}
