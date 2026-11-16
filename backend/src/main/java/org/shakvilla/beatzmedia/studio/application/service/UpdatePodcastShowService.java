package org.shakvilla.beatzmedia.studio.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.OriginCountry;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;
import org.shakvilla.beatzmedia.studio.application.port.in.PodcastShowView;
import org.shakvilla.beatzmedia.studio.application.port.in.UpdatePodcastShow;
import org.shakvilla.beatzmedia.studio.application.port.out.PodcastCataloguePublisher;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

@ApplicationScoped
public class UpdatePodcastShowService implements UpdatePodcastShow {
  private final StudioRepository repository;
  private final PodcastCataloguePublisher publisher;
  private final AuditWriter audit;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public UpdatePodcastShowService(StudioRepository repository, PodcastCataloguePublisher publisher,
      AuditWriter audit, IdGenerator ids, Clock clock) {
    this.repository = repository;
    this.publisher = publisher;
    this.audit = audit;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PodcastShowView update(ArtistId artist, ShowId id, UpdateShowCommand command) {
    if (command == null || command.title() == null || command.title().isBlank()) {
      throw new ValidationException("title is required", "title");
    }
    if (command.category() == null || command.category().isBlank()) {
      throw new ValidationException("category is required", "category");
    }
    var show = repository.findShow(artist, id)
        .orElseThrow(() -> new NotFoundException("Show not found: " + id.value()));
    var saved = repository.saveShow(show.withDetails(command.title(), command.category(),
        command.description(), OriginCountry.normalize(command.originCountry())));
    audit.append(new AuditEntry(ids.newId(), artist.value(), "UPDATE_PODCAST_SHOW",
        "PodcastShow", id.value(), AuditType.CATALOG, null, clock.now()));
    // The fan catalogue is a separate projection. Refresh each published episode so its show
    // title/category/description and episode publisher match the newly saved Studio details.
    repository.findEpisodes(artist).stream()
        .filter(episode -> episode.showId().equals(id)
            && episode.status() == EpisodeStatus.published && episode.durationSec() > 0)
        .forEach(episode -> publisher.publish(artist, episode));
    return ListStudioPodcastShowsService.toView(saved);
  }
}
