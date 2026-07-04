package org.shakvilla.beatzmedia.podcasts.application.port.out;

import java.time.Duration;
import java.time.Instant;

import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;

/**
 * Output port: mints signed, time-boxed audio delivery URLs; full HLS or the 30s server-clipped
 * preview rendition. Adapter delegates to the media module's {@code MediaService} output port
 * (WU-MED-1) — podcasts never constructs or signs a URL itself (INV-3). ADD §4.2.
 */
public interface MediaService {

  /**
   * Presign a delivery URL for the given episode's current media asset.
   *
   * @param episode the episode whose audio asset is being requested
   * @param preview true to sign the ≤30s server-clipped preview rendition; false for full audio —
   *     resolved server-side by the INV-3 gate before this call, never by a client-supplied flag
   * @param ttl signed URL time-to-live
   * @throws org.shakvilla.beatzmedia.podcasts.domain.MediaUnavailableException if no ready asset
   *     exists for the episode
   */
  SignedUrl issueSignedUrl(EpisodeId episode, boolean preview, Duration ttl);

  /** Result of presigning: the URL and its expiry instant. */
  record SignedUrl(String url, Instant expiresAt) {}
}
