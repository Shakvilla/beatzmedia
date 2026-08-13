package org.shakvilla.beatzmedia.playback.application.service;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.in.DownloadUrlResult;
import org.shakvilla.beatzmedia.playback.application.port.in.GetDownloadUrl;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.playback.application.port.out.DownloadPermissionReader;
import org.shakvilla.beatzmedia.playback.application.port.out.MediaService;
import org.shakvilla.beatzmedia.playback.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.playback.domain.DownloadNotAllowedException;
import org.shakvilla.beatzmedia.playback.domain.DownloadNotReadyException;
import org.shakvilla.beatzmedia.playback.domain.NotOwnedException;

/**
 * Application service for {@link GetDownloadUrl} — the only place a buy-to-own download is
 * authorised. Read-only; no transaction / DB write. Mirrors {@code GetStreamUrlService}'s shape.
 *
 * <p>The guard order is load-bearing: existence → ownership → permission → readiness. Ownership is
 * checked before permission so a stranger's refusal never varies with the release's download
 * setting, and permission before readiness so a refused caller never causes a signing round-trip.
 */
@ApplicationScoped
public class GetDownloadUrlService implements GetDownloadUrl {

  /**
   * The container the LOSSLESS rendition is produced in. Fixed by the transcoder
   * ({@code FfmpegAudioTranscoderAdapter} emits {@code delivery/{id}/lossless.flac}), not a tunable
   * — this is a statement about the bytes being served, so it travels with the variant rather than
   * coming from configuration.
   */
  private static final String LOSSLESS_FORMAT = "flac";

  private final CatalogReader catalogReader;
  private final OwnershipReader ownershipReader;
  private final DownloadPermissionReader downloadPermissionReader;
  private final MediaService mediaService;
  private final long signedUrlTtlSeconds;

  @Inject
  public GetDownloadUrlService(
      CatalogReader catalogReader,
      OwnershipReader ownershipReader,
      DownloadPermissionReader downloadPermissionReader,
      MediaService mediaService,
      @ConfigProperty(name = "beatz.signed-url-ttl-seconds", defaultValue = "300")
          long signedUrlTtlSeconds) {
    this.catalogReader = catalogReader;
    this.ownershipReader = ownershipReader;
    this.downloadPermissionReader = downloadPermissionReader;
    this.mediaService = mediaService;
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
  }

  @Override
  public DownloadUrlResult getDownloadUrl(TrackId track, AccountId caller) {
    catalogReader.getTrack(track).orElseThrow(() -> new TrackNotFoundException(track.value()));

    if (!ownershipReader.isOwned(caller, track)) {
      throw new NotOwnedException(track.value());
    }

    // THE rule this whole feature turns on: the caller's own GRANT is the authority, never
    // release.downloadable. The permission was captured onto the grant at settlement; the release's
    // current setting governs FUTURE sales only. Substituting it here would retract downloads from
    // people who already paid, the moment an artist changed their mind.
    if (!downloadPermissionReader.mayDownload(caller, track)) {
      throw new DownloadNotAllowedException(track.value());
    }

    return mediaService
        .issueLosslessUrl(track, Duration.ofSeconds(signedUrlTtlSeconds))
        .map(signed -> new DownloadUrlResult(signed.url(), signed.expiresAt(), LOSSLESS_FORMAT))
        // Empty means the FLAC does not exist yet. Refused rather than quietly downgraded to the
        // AAC streaming rendition (INV-3's sibling concern): a lossless purchase gets lossless
        // bytes or nothing.
        .orElseThrow(() -> new DownloadNotReadyException(track.value()));
  }
}
