package org.shakvilla.beatzmedia.playback.application.port.in;

import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.identity.domain.AccountId;

/**
 * Input port: resolve ownership server-side and return a signed, time-boxed audio URL — the full
 * rendition for owned/free tracks, the server-clipped preview for a for-sale track the caller does
 * not own (INV-3). The preview cap is physical, not advisory: the signed object is only
 * {@code beatz.preview-seconds} long, and that same value is what {@code previewSeconds} reports.
 * LLFR-PLAYBACK-01.1. Playback ADD §4.1.
 *
 * <p>Trigger: {@code GET /v1/tracks/:id/stream}. Auth: optional — anonymous caller is
 * {@link Optional#empty()} and is treated as not-owning for {@code for-sale} tracks. Unknown track
 * → {@link org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException} (404 TRACK_NOT_FOUND).
 */
public interface GetStreamUrl {

  StreamUrlResult getStreamUrl(TrackId track, Optional<AccountId> caller);
}
