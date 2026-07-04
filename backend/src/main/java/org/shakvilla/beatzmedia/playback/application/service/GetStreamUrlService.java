package org.shakvilla.beatzmedia.playback.application.service;

import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.playback.application.port.in.GetStreamUrl;
import org.shakvilla.beatzmedia.playback.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader;
import org.shakvilla.beatzmedia.playback.application.port.out.CatalogReader.TrackPlaybackInfo;
import org.shakvilla.beatzmedia.playback.application.port.out.MediaService;
import org.shakvilla.beatzmedia.playback.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.playback.domain.PlaybackMode;
import org.shakvilla.beatzmedia.playback.domain.StreamDecision;

/**
 * Application service for {@link GetStreamUrl} (LLFR-PLAYBACK-01.1). The sole INV-3 enforcement
 * point: the rendition (FULL vs PREVIEW) is decided here, server-side, from catalog ownership kind
 * + the caller's ownership grant — never from a client-supplied flag. Read-only; no transaction /
 * DB write. Playback ADD §4.1 / §8.
 */
@ApplicationScoped
public class GetStreamUrlService implements GetStreamUrl {

  private final CatalogReader catalogReader;
  private final OwnershipReader ownershipReader;
  private final MediaService mediaService;
  private final long signedUrlTtlSeconds;

  @Inject
  public GetStreamUrlService(
      CatalogReader catalogReader,
      OwnershipReader ownershipReader,
      MediaService mediaService,
      @ConfigProperty(name = "beatz.signed-url-ttl-seconds", defaultValue = "300")
          long signedUrlTtlSeconds) {
    this.catalogReader = catalogReader;
    this.ownershipReader = ownershipReader;
    this.mediaService = mediaService;
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
  }

  @Override
  public StreamUrlResult getStreamUrl(TrackId track, Optional<AccountId> caller) {
    TrackPlaybackInfo info =
        catalogReader
            .getTrack(track)
            .orElseThrow(() -> new TrackNotFoundException(track.value()));

    // INV-3: ownership is only queried when it can matter (for-sale + a known caller); an
    // anonymous caller is treated as not-owning without an ownership round-trip.
    boolean owned =
        caller.isPresent() && ownershipReader.isOwned(caller.get(), track);

    PlaybackMode mode = StreamDecision.decide(info.ownership(), owned);

    Duration ttl = Duration.ofSeconds(signedUrlTtlSeconds);
    MediaService.SignedUrl signed = mediaService.issueSignedUrl(track, mode, ttl);

    Optional<Integer> previewSeconds =
        mode == PlaybackMode.PREVIEW ? Optional.of(30) : Optional.empty();

    return new StreamUrlResult(signed.url(), previewSeconds, signed.expiresAt());
  }
}
