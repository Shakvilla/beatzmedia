package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetRelease;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
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
  public StudioReleaseDetailView get(ReleaseId id, ArtistId requestingArtist) {
    Release release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    var tracks = repo.tracksByIds(
        release.getTracks().stream().map(ReleaseTrack::trackId).toList());
    return ReleaseViewMapper.toDetailView(release, tracks);
  }
}
