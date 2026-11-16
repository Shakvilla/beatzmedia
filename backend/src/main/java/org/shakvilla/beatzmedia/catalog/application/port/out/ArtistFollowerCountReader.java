package org.shakvilla.beatzmedia.catalog.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** Current public follower total, sourced from active follow relationships. */
@FunctionalInterface
public interface ArtistFollowerCountReader {
  long count(ArtistId artistId);
}
