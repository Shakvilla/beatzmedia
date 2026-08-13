package org.shakvilla.beatzmedia.playback.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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

  /**
   * Presign a delivery URL for the track's LOSSLESS (FLAC) rendition — the download payload.
   *
   * <p>The variant is fixed by the method rather than passed in: there is exactly one rendition a
   * download may ever be, and no caller should be able to ask this port for the lossy one under the
   * name "download". The permission decision has already been made by the time this is called.
   *
   * @return empty when the FLAC does not exist yet (no media asset at all, or the asset carries no
   *     lossless key). Deliberately NOT a fall back to {@code FULL} — see
   *     {@code DownloadNotReadyException}. The caller turns this into 409 DOWNLOAD_NOT_READY.
   * @throws org.shakvilla.beatzmedia.playback.domain.MediaUnavailableException if the media module
   *     itself is unreachable or failing (a 503 condition, distinct from "not ready")
   */
  Optional<SignedUrl> issueLosslessUrl(TrackId track, Duration ttl);

  /** Result of presigning: the URL and its expiry instant. */
  record SignedUrl(String url, Instant expiresAt) {}
}
