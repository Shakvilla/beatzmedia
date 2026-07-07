package org.shakvilla.beatzmedia.analytics.adapter.out.integration;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.analytics.application.port.out.ArtistResolver;
import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;

/**
 * Implements analytics' {@link ArtistResolver} output port by calling catalog's {@link GetTrack}
 * INPUT port in-process — never reading catalog's tables directly. Mirrors
 * {@code playback.adapter.out.integration.CatalogReaderAdapter}. Analytics ADD §4.1.
 */
@ApplicationScoped
public class ArtistResolverAdapter implements ArtistResolver {

  private final GetTrack getTrack;

  @Inject
  public ArtistResolverAdapter(GetTrack getTrack) {
    this.getTrack = getTrack;
  }

  @Override
  public Optional<ArtistId> artistOfTrack(TrackId track) {
    try {
      TrackView view = getTrack.get(track, Optional.empty());
      return Optional.ofNullable(view.artistId()).map(ArtistId::new);
    } catch (TrackNotFoundException e) {
      return Optional.empty();
    }
  }
}
