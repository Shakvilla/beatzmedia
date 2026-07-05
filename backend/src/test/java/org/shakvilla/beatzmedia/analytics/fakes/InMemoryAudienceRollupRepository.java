package org.shakvilla.beatzmedia.analytics.fakes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.analytics.application.port.out.AudienceRollupRepository;
import org.shakvilla.beatzmedia.analytics.domain.AudienceRollup;
import org.shakvilla.beatzmedia.analytics.domain.Grain;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** In-memory fake for {@link AudienceRollupRepository}, keyed like the real unique constraint. */
public class InMemoryAudienceRollupRepository implements AudienceRollupRepository {

  private final Map<String, AudienceRollup> rows = new LinkedHashMap<>();

  private static String key(ArtistId artistId, LocalDate bucket, Grain grain) {
    return artistId.value() + "|" + bucket + "|" + grain;
  }

  @Override
  public void upsert(AudienceRollup rollup) {
    rows.put(key(rollup.artistId(), rollup.bucket().bucket(), rollup.bucket().grain()), rollup);
  }

  @Override
  public Optional<AudienceRollup> find(ArtistId artistId, LocalDate bucket, Grain grain) {
    return Optional.ofNullable(rows.get(key(artistId, bucket, grain)));
  }

  @Override
  public List<AudienceRollup> findRange(ArtistId artistId, Grain grain, LocalDate from, LocalDate to) {
    return rows.values().stream()
        .filter(r -> r.artistId().equals(artistId))
        .filter(r -> r.bucket().grain() == grain)
        .filter(r -> !r.bucket().bucket().isBefore(from) && !r.bucket().bucket().isAfter(to))
        .sorted((a, b) -> a.bucket().bucket().compareTo(b.bucket().bucket()))
        .toList();
  }

  public int size() {
    return rows.size();
  }
}
