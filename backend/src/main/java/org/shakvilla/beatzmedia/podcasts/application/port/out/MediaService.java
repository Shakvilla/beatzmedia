package org.shakvilla.beatzmedia.podcasts.application.port.out;

import java.time.Duration;
import java.time.Instant;

import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * Output port: mints signed, time-boxed audio delivery URLs; the full rendition or the
 * server-clipped preview, each a single presigned {@code .m4a} object (ADR-34 — an HLS playlist's
 * segments are referenced relatively and would be unsigned against a private bucket). Adapter
 * delegates to the media module's {@code MediaService} output port
 * (WU-MED-1) — podcasts never constructs or signs a URL itself (INV-3). ADD §4.2.
 */
public interface MediaService {

  /**
   * Presign a delivery URL for the given episode's current media asset.
   *
   * @param episode the episode whose audio asset is being requested
   * @param preview true to sign the server-clipped preview rendition ({@code beatz.preview-seconds}
   *     long); false for full audio —
   *     resolved server-side by the INV-3 gate before this call, never by a client-supplied flag
   * @param ttl signed URL time-to-live
   * @throws org.shakvilla.beatzmedia.podcasts.domain.MediaUnavailableException if no ready asset
   *     exists for the episode
   */
  SignedUrl issueSignedUrl(EpisodeId episode, boolean preview, Duration ttl);

  /** Result of presigning: the URL and its expiry instant. */
  record SignedUrl(String url, Instant expiresAt) {}
}
