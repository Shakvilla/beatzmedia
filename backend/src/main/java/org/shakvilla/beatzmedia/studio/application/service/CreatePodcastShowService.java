package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.studio.application.port.in.CreatePodcastShow;
import org.shakvilla.beatzmedia.studio.application.port.in.PodcastShowView;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.PodcastShow;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

/** Application service for {@link CreatePodcastShow} — LLFR-STUDIO-02.1. Studio ADD §4.1. */
@ApplicationScoped
public class CreatePodcastShowService implements CreatePodcastShow {

  private final StudioRepository repository;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public CreatePodcastShowService(StudioRepository repository, IdGenerator ids, Clock clock) {
    this.repository = repository;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PodcastShowView create(ArtistId artist, CreatePodcastShowCommand cmd) {
    if (cmd == null || cmd.title() == null || cmd.title().isBlank()) {
      throw new ValidationException("title is required", "title");
    }
    if (cmd.category() == null || cmd.category().isBlank()) {
      throw new ValidationException("category is required", "category");
    }
    PodcastShow show =
        PodcastShow.create(new ShowId(ids.newId()), artist, cmd.title(), cmd.category(), clock.now());
    PodcastShow saved = repository.saveShow(show);
    return ListStudioPodcastShowsService.toView(saved);
  }
}
