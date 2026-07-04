package org.shakvilla.beatzmedia.podcasts.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.podcasts.application.port.in.GetPodcast;
import org.shakvilla.beatzmedia.podcasts.application.port.in.PodcastView;
import org.shakvilla.beatzmedia.podcasts.application.port.out.PodcastRepository;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastNotFoundException;

/** Application service for LLFR-PODCAST-01.2 (show detail). ADD §4.1. */
@ApplicationScoped
@Transactional
public class GetPodcastService implements GetPodcast {

  private final PodcastRepository repository;

  @Inject
  public GetPodcastService(PodcastRepository repository) {
    this.repository = repository;
  }

  @Override
  public PodcastView get(PodcastId id) {
    return repository
        .findShow(id)
        .map(PodcastMapper::toView)
        .orElseThrow(() -> new PodcastNotFoundException(id.value()));
  }
}
