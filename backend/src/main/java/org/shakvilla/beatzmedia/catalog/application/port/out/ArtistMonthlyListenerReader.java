package org.shakvilla.beatzmedia.catalog.application.port.out;

import java.time.Instant;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** Counts distinct identified listeners with recorded plays in a time window. */
@FunctionalInterface
public interface ArtistMonthlyListenerReader {
  long count(ArtistId artistId, Instant since, Instant asOf);
}
