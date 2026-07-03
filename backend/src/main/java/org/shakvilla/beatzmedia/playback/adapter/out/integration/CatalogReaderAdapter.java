package org.shakvilla.beatzmedia.playback.adapter.out.integration;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrackPlaybackInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackPlaybackInfoView;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;

/**
 * Implements playback's {@link CatalogReader} output port by calling catalog's
 * {@link GetTrackPlaybackInfo} INPUT port in-process — never reading catalog's tables directly.
 * Playback ADD §5.2.
 */
@ApplicationScoped
public class CatalogReaderAdapter implements CatalogReader {

  private final GetTrackPlaybackInfo getTrackPlaybackInfo;

  @Inject
  public CatalogReaderAdapter(GetTrackPlaybackInfo getTrackPlaybackInfo) {
    this.getTrackPlaybackInfo = getTrackPlaybackInfo;
  }

  @Override
  public Optional<TrackPlaybackInfo> getTrack(TrackId track) {
    return getTrackPlaybackInfo.get(track).map(CatalogReaderAdapter::toPlaybackInfo);
  }

  private static TrackPlaybackInfo toPlaybackInfo(TrackPlaybackInfoView view) {
    TrackOwnership ownership =
        "for-sale".equals(view.ownership()) ? TrackOwnership.FOR_SALE : TrackOwnership.FREE;
    return new TrackPlaybackInfo(new TrackId(view.id()), ownership);
  }
}
