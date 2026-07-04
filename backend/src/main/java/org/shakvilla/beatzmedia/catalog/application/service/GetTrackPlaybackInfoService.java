package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetTrackPlaybackInfo;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackPlaybackInfoView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/**
 * Application service for {@link GetTrackPlaybackInfo}. Read-only projection of the track's own
 * intrinsic {@code ownership} column — no per-caller decoration (the caller resolves ownership
 * itself, e.g. playback via its own {@code OwnershipReader}). Catalog ADD §4.1.
 */
@ApplicationScoped
@Transactional
public class GetTrackPlaybackInfoService implements GetTrackPlaybackInfo {

  private final CatalogRepository catalogRepository;

  @Inject
  public GetTrackPlaybackInfoService(CatalogRepository catalogRepository) {
    this.catalogRepository = catalogRepository;
  }

  @Override
  public Optional<TrackPlaybackInfoView> get(TrackId id) {
    return catalogRepository
        .findTrack(id)
        .map(t -> new TrackPlaybackInfoView(t.getId().value(), t.getOwnership().wireValue()));
  }
}
