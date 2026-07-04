package org.shakvilla.beatzmedia.podcasts.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.podcasts.application.port.in.ListPodcasts;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastCategory;

/** Application service for LLFR-PODCAST-01.1 (browse shows). ADD §4.1. */
@ApplicationScoped
@Transactional
public class ListPodcastsService implements ListPodcasts {

  private final PodcastRepository repository;

  @Inject
  public ListPodcastsService(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public Page<PodcastView> list(Optional<PodcastCategory> category, PageRequest page) {
    Page<org.shakvilla.beatzmedia.podcasts.domain.Podcast> shows =
        repository.findShows(category, page);
    return new Page<>(
        shows.items().stream().map(PodcastMapper::toView).toList(),
        shows.page(),
        shows.size(),
        shows.total());
  }
}
