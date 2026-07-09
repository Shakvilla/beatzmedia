package org.shakvilla.beatzmedia.studio.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.studio.application.port.in.ListStudioPodcastShows;
import org.shakvilla.beatzmedia.studio.application.port.in.PodcastShowView;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;

/** Application service for {@link ListStudioPodcastShows} — LLFR-STUDIO-02.1. Studio ADD §4.1. */
@ApplicationScoped
public class ListStudioPodcastShowsService implements ListStudioPodcastShows {

  private final StudioRepository repository;

  @Inject
  public ListStudioPodcastShowsService(StudioRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public List<PodcastShowView> list(ArtistId artist) {
    return repository.findShows(artist).stream().map(ListStudioPodcastShowsService::toView).toList();
  }

  static PodcastShowView toView(PodcastShow show) {
    return new PodcastShowView(show.id().value(), show.title(), show.category());
  }
}
