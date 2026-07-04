package org.shakvilla.beatzmedia.playback.adapter.out.integration;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.media.domain.DeliveryVariant;
import org.shakvilla.beatzmedia.media.domain.MediaAssetId;
import org.shakvilla.beatzmedia.media.domain.OwnerRef;
import org.shakvilla.beatzmedia.media.domain.SignedUrl;
import org.shakvilla.beatzmedia.playback.application.port.out.MediaService;
import org.shakvilla.beatzmedia.playback.domain.MediaUnavailableException;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;

/**
 * Implements playback's {@link MediaService} output port by calling the media module's
 * {@code MediaService} INPUT/output port (WU-MED-1) in-process — playback never constructs or
 * signs a URL itself (INV-3). Resolves the track's current media asset via
 * {@code FindAssetForOwnerUseCase} using the same {@link OwnerRef} convention the catalog upload
 * path stores media under ({@code ("catalog", trackId)}). Playback ADD §5.2.
 */
@ApplicationScoped
public class MediaServiceAdapter implements MediaService {

  /** Matches the owner-ref module string used by catalog's upload path (WU-CAT-3). */
  private static final String CATALOG_OWNER_MODULE = "catalog";

  private final org.shakvilla.beatzmedia.media.application.port.out.MediaService mediaService;

  @Inject
  public MediaServiceAdapter(
      org.shakvilla.beatzmedia.media.application.port.out.MediaService mediaService) {
    this.mediaService = mediaService;
  }

  @Override
  public SignedUrl issueSignedUrl(TrackId track, PlaybackMode mode, Duration ttl) {
    OwnerRef ownerRef = new OwnerRef(CATALOG_OWNER_MODULE, track.value());
    MediaAssetId assetId =
        mediaService
            .findAssetIdForOwner(ownerRef)
            .orElseThrow(
                () ->
                    new MediaUnavailableException(
                        "No media asset available for track: " + track.value()));

    DeliveryVariant variant =
        mode == PlaybackMode.FULL ? DeliveryVariant.FULL : DeliveryVariant.PREVIEW;

    try {
      org.shakvilla.beatzmedia.media.domain.SignedUrl signed =
          mediaService.issueSignedUrl(assetId, variant, ttl);
      return new SignedUrl(signed.url(), signed.expiresAt());
    } catch (RuntimeException e) {
      // Translate any media outage/illegal-state (e.g. asset not READY) into the mapped 503 —
      // never an unmapped 500 (Playback ADD §5.1).
      throw new MediaUnavailableException(
          "Media delivery unavailable for track: " + track.value());
    }
  }
}
