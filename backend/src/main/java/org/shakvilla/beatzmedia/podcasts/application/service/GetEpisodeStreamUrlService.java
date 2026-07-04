package org.shakvilla.beatzmedia.podcasts.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.podcasts.application.port.in.GetEpisodeStreamUrl;
import org.shakvilla.beatzmedia.podcasts.application.port.in.StreamUrlResult;
import org.shakvilla.beatzmedia.podcasts.application.port.out.MediaService;
import org.shakvilla.beatzmedia.podcasts.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeAccess;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeNotFoundException;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;

/**
 * Application service for {@link GetEpisodeStreamUrl} (LLFR-PODCAST-01.3). The sole INV-3
 * enforcement point for podcast episode streaming: the rendition (full vs. preview) is decided
 * here, server-side, from the episode's premium/early-access flags + the caller's ownership grant
 * — never from a client-supplied flag. Read-only; no transaction / DB write. Mirrors playback's
 * {@code GetStreamUrlService} (WU-PLY-1). ADD §4.1 / §8.
 */
@ApplicationScoped
public class GetEpisodeStreamUrlService implements GetEpisodeStreamUrl {

  private final PodcastRepository repository;
  private final OwnershipReader ownershipReader;
  private final MediaService mediaService;
  private final Clock clock;
  private final long signedUrlTtlSeconds;

  @Inject
  public GetEpisodeStreamUrlService(
      PodcastRepository repository,
      OwnershipReader ownershipReader,
      MediaService mediaService,
      Clock clock,
      @ConfigProperty(name = "beatz.signed-url-ttl-seconds", defaultValue = "300")
          long signedUrlTtlSeconds) {
    this.repository = repository;
    this.ownershipReader = ownershipReader;
    this.mediaService = mediaService;
    this.clock = clock;
    this.signedUrlTtlSeconds = signedUrlTtlSeconds;
  }

  @Override
  public StreamUrlResult getStreamUrl(EpisodeId episodeId, Optional<AccountId> caller) {
    PodcastEpisode episode =
        repository
            .findEpisode(episodeId)
            .orElseThrow(() -> new EpisodeNotFoundException(episodeId.value()));

    // INV-3: ownership is only queried when it can matter (gated episode + a known caller); an
    // anonymous caller is treated as not-owning without an ownership round-trip.
    boolean owned =
        episode.isGated() && caller.isPresent() && ownershipReader.ownsEpisode(caller.get(), episodeId);

    Instant now = clock.now();
    boolean publicNow = episode.publicAt().map(publicAt -> !now.isBefore(publicAt)).orElse(false);

    EpisodeAccess access =
        EpisodeAccess.decide(episode, owned, publicNow, EpisodeAccess.DEFAULT_PREVIEW_SEC);

    Duration ttl = Duration.ofSeconds(signedUrlTtlSeconds);
    MediaService.SignedUrl signed =
        mediaService.issueSignedUrl(episodeId, access.previewOnly(), ttl);

    Optional<Integer> previewSeconds =
        access.previewOnly() ? Optional.of(access.previewSec()) : Optional.empty();

    return new StreamUrlResult(signed.url(), previewSeconds, signed.expiresAt());
  }
}
