package org.shakvilla.beatzmedia.playback.application.port.out;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.domain.TrackOwnership;

/**
 * Output port: reads track metadata needed to resolve the ownership kind + existence. Adapter
 * calls catalog's {@code GetTrack} input port in-process — playback never reads catalog's tables
 * directly. Playback ADD §4.2.
 */
public interface CatalogReader {

  Optional<TrackPlaybackInfo> getTrack(TrackId track);

  /** Projection carrying only what playback needs from a track. */
  record TrackPlaybackInfo(TrackId id, TrackOwnership ownership) {}
}
