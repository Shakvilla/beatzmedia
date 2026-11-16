package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.studio.domain.StudioProfileImagesUpdated;

/** Projects persisted studio avatar/banner URLs into the fan-facing artist catalog profile. */
@ApplicationScoped
public class StudioProfileImagesUpdatedObserver {

  private final CatalogRepository catalog;

  public StudioProfileImagesUpdatedObserver(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onImagesUpdated(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) StudioProfileImagesUpdated event) {
    catalog.updateArtistProfileImages(
        new ArtistId(event.artistId()), event.avatarUrl(), event.bannerUrl(), event.updatedAt());
  }
}
