package org.shakvilla.beatzmedia.playback.application.port.out;

import java.time.Duration;
import java.time.Instant;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;

/**
 * Output port: mints signed, time-boxed audio delivery URLs; full HLS or the 30s server-clipped
 * preview rendition. Adapter delegates to the media module's {@code MediaService} /
 * {@code IssueDeliveryUrlUseCase} input port (WU-MED-1) — playback never constructs or signs a URL
 * itself. Playback ADD §4.2.
 */
public interface MediaService {

  /**
   * Presign a delivery URL for the given track's current media asset.
   *
   * @param track the track whose audio asset is being requested
   * @param mode  FULL or PREVIEW — resolved server-side by the INV-3 gate before this call
   * @param ttl   URL time-to-live
   * @throws org.shakvilla.beatzmedia.playback.domain.MediaUnavailableException if no ready asset
   *     exists for the track
   */
  SignedUrl issueSignedUrl(TrackId track, PlaybackMode mode, Duration ttl);

  /** Result of presigning: the URL and its expiry instant. */
  record SignedUrl(String url, Instant expiresAt) {}
}
