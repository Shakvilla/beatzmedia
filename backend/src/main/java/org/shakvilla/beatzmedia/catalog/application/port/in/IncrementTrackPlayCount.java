package org.shakvilla.beatzmedia.catalog.application.port.in;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Input port for applying a successfully recorded playback event to the catalog play-count
 * projection.
 */
public interface IncrementTrackPlayCount {
  void increment(TrackId trackId);
}
