package org.shakvilla.beatzmedia.playback.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;

/** In-memory fake for {@link CatalogReader} used in unit tests. */
public class FakeCatalogReader implements CatalogReader {

  private final Map<String, TrackOwnership> tracks = new HashMap<>();

  public FakeCatalogReader seed(String trackId, TrackOwnership ownership) {
    tracks.put(trackId, ownership);
    return this;
  }

  @Override
  public Optional<TrackPlaybackInfo> getTrack(TrackId track) {
    TrackOwnership ownership = tracks.get(track.value());
    if (ownership == null) {
      return Optional.empty();
    }
    return Optional.of(new TrackPlaybackInfo(track, ownership));
  }
}
