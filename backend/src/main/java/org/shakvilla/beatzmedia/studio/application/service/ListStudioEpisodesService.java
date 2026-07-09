package org.shakvilla.beatzmedia.studio.application.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.application.port.in.ListStudioEpisodes;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;

/** Application service for {@link ListStudioEpisodes} — LLFR-STUDIO-02.2. Studio ADD §4.1. */
@ApplicationScoped
public class ListStudioEpisodesService implements ListStudioEpisodes {

  private final StudioRepository repository;

  @Inject
  public ListStudioEpisodesService(StudioRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public List<EpisodeView> list(ArtistId artist) {
    Map<String, String> showTitleById =
        repository.findShows(artist).stream()
            .collect(Collectors.toMap(s -> s.id().value(), PodcastShow::title));
    return repository.findEpisodes(artist).stream()
        .map(e -> EpisodeMapper.toView(e, showTitleById.get(e.showId().value())))
        .toList();
  }
}
