package org.shakvilla.beatzmedia.playback.application.port.out;

import java.time.Duration;
import java.time.Instant;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;

/**
 * Output port: mints signed, time-boxed audio delivery URLs; the full rendition or the
 * server-clipped preview, each a single presigned {@code .m4a} object (ADR-34 — an HLS playlist's
 * segments are referenced relatively and would be unsigned against a private bucket). Adapter
 * delegates to the media module's {@code MediaService} /
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
