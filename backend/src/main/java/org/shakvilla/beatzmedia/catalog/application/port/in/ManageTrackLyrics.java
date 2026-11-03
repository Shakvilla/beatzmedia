package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.List;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.LyricLine;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** Artist-owned lyrics update. Lyrics publish immediately and do not enter release moderation. */
public interface ManageTrackLyrics {
  List<LyricLine> get(ReleaseId releaseId, TrackId trackId, ArtistId artistId);

  void save(ReleaseId releaseId, TrackId trackId, ArtistId artistId, List<LyricLine> lines);
}
