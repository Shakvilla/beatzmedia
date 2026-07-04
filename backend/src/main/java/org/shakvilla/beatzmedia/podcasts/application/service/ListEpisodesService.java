package org.shakvilla.beatzmedia.podcasts.application.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListEpisodes;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastEpisodeView;
import org.shakvilla.beatzmedia.podcasts.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.EpisodeId;
import org.shakvilla.beatzmedia.podcasts.domain.Podcast;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastEpisode;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;

/**
 * Application service for LLFR-PODCAST-01.3 (episode list, per-caller ownership + early-access
 * decoration). Ownership is resolved via {@link OwnershipReader} only when a caller is present
 * (INV-3 — an anonymous caller is treated as not-owning without an ownership round-trip, mirroring
 * playback's {@code GetStreamUrlService}). ADD §4.1 / §8.
 */
@ApplicationScoped
@Transactional
public class ListEpisodesService implements ListEpisodes {

  private final PodcastRepository repository;
  private final OwnershipReader ownershipReader;

  @Inject
  public ListEpisodesService(PodcastRepository repository, OwnershipReader ownershipReader) {
    this.repository = repository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public List<PodcastEpisodeView> list(PodcastId id, Optional<AccountId> caller) {
    Podcast show = repository.findShow(id).orElseThrow(() -> new PodcastNotFoundException(id.value()));
    List<PodcastEpisode> episodes = repository.findEpisodes(id);

    Set<EpisodeId> owned;
    if (caller.isPresent()) {
      Set<EpisodeId> candidates =
          episodes.stream()
              .filter(PodcastEpisode::isGated)
              .map(PodcastEpisode::id)
              .collect(Collectors.toUnmodifiableSet());
      owned =
          candidates.isEmpty()
              ? Set.of()
              : ownershipReader.ownedEpisodes(caller.get(), candidates);
    } else {
      owned = Set.of();
    }

    return episodes.stream()
        .map(ep -> PodcastMapper.toView(ep, show.title(), owned.contains(ep.id())))
        .toList();
  }
}
