package org.shakvilla.beatzmedia.catalog.adapter.in.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.application.port.out.TrackSearchProjection;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseRevisionApproved;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;

@ApplicationScoped
public class ReleaseRevisionSearchProjectionObserver {
  private final CatalogRepository catalog;
  private final TrackSearchProjection search;

  @Inject
  public ReleaseRevisionSearchProjectionObserver(CatalogRepository catalog, TrackSearchProjection search) {
    this.catalog = catalog;
    this.search = search;
  }

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onApproved(@Observes(during = TransactionPhase.AFTER_SUCCESS) ReleaseRevisionApproved event) {
    boolean visible = catalog.findRelease(new ReleaseId(event.releaseId()))
        .map(release -> release.getStatus() == ReleaseStatus.live).orElse(false);
    for (var track : catalog.tracksByIds(catalog.findRelease(new ReleaseId(event.releaseId()))
        .map(release -> release.getTracks().stream().map(rt -> rt.trackId()).toList())
        .orElseGet(java.util.List::of))) {
      search.index(track, visible);
    }
  }
}
