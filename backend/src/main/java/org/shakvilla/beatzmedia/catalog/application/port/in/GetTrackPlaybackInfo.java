package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Input port: the minimal track projection other modules need to make ownership-aware decisions
 * (playback's stream-URL gate) — existence + intrinsic commercial kind, with no per-caller
 * decoration. Distinct from {@link GetTrack} (which throws on unknown id and decorates
 * per-caller ownership/price for the public track-detail endpoint). Catalog ADD §4.1.
 */
public interface GetTrackPlaybackInfo {

  Optional<TrackPlaybackInfoView> get(TrackId id);
}
