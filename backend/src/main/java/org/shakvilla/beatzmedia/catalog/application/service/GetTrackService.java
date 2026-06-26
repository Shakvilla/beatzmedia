package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrack;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.OwnershipReader;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.TrackNotFoundException;

/**
 * Application service for LLFR-CATALOG-01.6 (track detail). Catalog ADD §4.1.
 */
@ApplicationScoped
public class GetTrackService implements GetTrack {

  private final CatalogRepository catalogRepository;
  private final OwnershipReader ownershipReader;

  @Inject
  public GetTrackService(CatalogRepository catalogRepository, OwnershipReader ownershipReader) {
    this.catalogRepository = catalogRepository;
    this.ownershipReader = ownershipReader;
  }

  @Override
  public TrackView get(TrackId id, Optional<String> callerId) {
    return catalogRepository.findTrack(id)
        .map(t -> TrackMapper.toView(t, callerId, ownershipReader))
        .orElseThrow(() -> new TrackNotFoundException(id.value()));
  }
}
