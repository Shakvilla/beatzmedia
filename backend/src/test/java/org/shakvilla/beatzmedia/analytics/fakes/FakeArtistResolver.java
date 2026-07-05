package org.shakvilla.beatzmedia.analytics.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.shakvilla.beatzmedia.analytics.application.port.out.ArtistResolver;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** In-memory fake for {@link ArtistResolver}. */
public class FakeArtistResolver implements ArtistResolver {

  private final Map<String, String> trackToArtist = new HashMap<>();

  public void seed(String trackId, String artistId) {
    trackToArtist.put(trackId, artistId);
  }

  @Override
  public Optional<ArtistId> artistOfTrack(TrackId track) {
    String artist = trackToArtist.get(track.value());
    return artist == null ? Optional.empty() : Optional.of(new ArtistId(artist));
  }
}
