package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link GetRelease}. Returns the release only if owned by the requesting
 * artist. LLFR-CATALOG-02.3.
 */
@ApplicationScoped
public class GetReleaseService implements GetRelease {

  private final CatalogRepository repo;

  @Inject
  public GetReleaseService(CatalogRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional
  public StudioReleaseView get(ReleaseId id, ArtistId requestingArtist) {
    Release release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    return toView(release);
  }

  private StudioReleaseView toView(Release r) {
    String date = r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—";
    return new StudioReleaseView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        date,
        r.getTracks().size(),
        0L,
        MoneyView.ofMinor(0L),
        MoneyView.ofMinor(r.getListPriceMinor()));
  }
}
