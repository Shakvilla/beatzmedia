package org.shakvilla.beatzmedia.catalog.adapter.out.search;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.catalog.application.port.out.TrackSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.search.application.port.in.IndexEntityUseCase;

/** Drives track artwork changes through search's public indexing port. */
@ApplicationScoped
public class TrackSearchProjectionAdapter implements TrackSearchProjection {

  private final IndexEntityUseCase index;

  @Inject
  public TrackSearchProjectionAdapter(IndexEntityUseCase index) {
    this.index = index;
  }

  @Override
  public void index(Track track, boolean visible) {
    index.index(CatalogIndexDocuments.fromTrack(track, visible));
  }
}
