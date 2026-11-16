package org.shakvilla.beatzmedia.catalog.application.port.out;

import org.shakvilla.beatzmedia.catalog.domain.Track;

/** Output port for updating a track's document after its artist changes track artwork. */
public interface TrackSearchProjection {
  void index(Track track, boolean visible);
}
