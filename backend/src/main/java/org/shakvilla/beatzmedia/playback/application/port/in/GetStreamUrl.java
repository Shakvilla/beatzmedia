package org.shakvilla.beatzmedia.playback.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: resolve ownership server-side and return a signed, time-boxed audio URL — full HLS
 * for owned/free tracks, the 30s server-clipped preview for a for-sale track the caller does not
 * own (INV-3). LLFR-PLAYBACK-01.1. Playback ADD §4.1.
 *
 * <p>Trigger: {@code GET /v1/tracks/:id/stream}. Auth: optional — anonymous caller is
 * {@link Optional#empty()} and is treated as not-owning for {@code for-sale} tracks. Unknown track
 * → {@link org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException} (404 TRACK_NOT_FOUND).
 */
public interface GetStreamUrl {

  StreamUrlResult getStreamUrl(TrackId track, Optional<AccountId> caller);
}
