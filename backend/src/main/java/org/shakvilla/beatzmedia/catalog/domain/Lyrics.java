package org.shakvilla.beatzmedia.catalog.domain;

import java.util.List;

/**
 * Timed lyrics for a track. Entity; one-to-zero/one with Track. Catalog ADD §3 /
 * LLFR-CATALOG-01.6. Domain-layer; no framework imports.
 */
public final class Lyrics {

  private final TrackId trackId;
  private final List<LyricLine> lines;

  public Lyrics(TrackId trackId, List<LyricLine> lines) {
    this.trackId = trackId;
    this.lines = lines;
  }

  public TrackId getTrackId() {
    return trackId;
  }

  public List<LyricLine> getLines() {
    return lines;
  }
}
