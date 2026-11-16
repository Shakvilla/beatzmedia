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
import org.shakvilla.beatzmedia.studio.application.port.in.SetShowOriginCountry;
import org.shakvilla.beatzmedia.studio.application.port.out.StudioRepository;
import org.shakvilla.beatzmedia.studio.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.ShowId;

@ApplicationScoped
public class SetShowOriginCountryService implements SetShowOriginCountry {

  private final StudioRepository repository;
  private final AuditWriter audit;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public SetShowOriginCountryService(
      StudioRepository repository, AuditWriter audit, IdGenerator ids, Clock clock) {
    this.repository = repository;
    this.audit = audit;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public PodcastShowView set(ArtistId artist, ShowId showId, String originCountry) {
    if (originCountry == null) {
      throw new ValidationException("originCountry is required", "originCountry");
    }
    var show = repository.findShow(artist, showId)
        .orElseThrow(() -> new NotFoundException("Show not found: " + showId.value()));
    var saved = repository.saveShow(show.withOriginCountry(OriginCountry.normalize(originCountry)));
    audit.append(new AuditEntry(ids.newId(), artist.value(), "SET_SHOW_ORIGIN_COUNTRY",
        "PodcastShow", showId.value(), AuditType.CATALOG, null, clock.now()));
    return ListStudioPodcastShowsService.toView(saved);
  }
}
