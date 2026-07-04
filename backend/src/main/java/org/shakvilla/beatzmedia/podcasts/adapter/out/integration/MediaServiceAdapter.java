package org.shakvilla.beatzmedia.podcasts.adapter.out.integration;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.podcasts.application.port.out.MediaService;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.MediaUnavailableException;

/**
 * Implements podcasts' {@link MediaService} output port by calling the media module's
 * {@code MediaService} output port (WU-MED-1) in-process — podcasts never constructs or signs a
 * URL itself (INV-3). Resolves the episode's current media asset via
 * {@code findAssetIdForOwner} using the owner-ref convention {@code ("podcasts", episodeId)},
 * mirroring the {@code ("catalog", trackId)} convention playback uses for tracks (WU-PLY-1). ADD
 * §5.2.
 */
@ApplicationScoped
public class MediaServiceAdapter implements MediaService {

  /** Owner-ref module string this module's uploads (WU-STU-2) will store media under. */
  private static final String PODCASTS_OWNER_MODULE = "podcasts";

  private final org.shakvilla.beatzmedia.media.application.port.out.MediaService mediaService;

  @Inject
  public MediaServiceAdapter(
      org.shakvilla.beatzmedia.media.application.port.out.MediaService mediaService) {
    this.mediaService = mediaService;
  }

  @Override
  public MediaService.SignedUrl issueSignedUrl(EpisodeId episode, boolean preview, Duration ttl) {
    OwnerRef ownerRef = new OwnerRef(PODCASTS_OWNER_MODULE, episode.value());
    MediaAssetId assetId =
        mediaService
            .findAssetIdForOwner(ownerRef)
            .orElseThrow(
                () ->
                    new MediaUnavailableException(
                        "No media asset available for episode: " + episode.value()));

    DeliveryVariant variant = preview ? DeliveryVariant.PREVIEW : DeliveryVariant.FULL;

    try {
      org.shakvilla.beatzmedia.media.domain.SignedUrl signed =
          mediaService.issueSignedUrl(assetId, variant, ttl);
      return new MediaService.SignedUrl(signed.url(), signed.expiresAt());
    } catch (RuntimeException e) {
      // Translate any media outage/illegal-state (e.g. asset not READY) into the mapped 503 —
      // never an unmapped 500 (ADD §5.1).
      throw new MediaUnavailableException(
          "Media delivery unavailable for episode: " + episode.value());
    }
  }
}
