package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.IncrementTrackPlayCount;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;

/** Applies counted playback events to the catalog's denormalized lifetime play counter. */
@ApplicationScoped
public class IncrementTrackPlayCountService implements IncrementTrackPlayCount {

  private final CatalogRepository catalog;

  @Inject
  public IncrementTrackPlayCountService(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  @Override
  @Transactional
  public void increment(TrackId trackId) {
    catalog.incrementPlayCount(trackId);
  }
}
