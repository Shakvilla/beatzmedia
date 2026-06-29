package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.DeleteRelease;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseLiveException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for {@link DeleteRelease}. Only draft/in_review releases may be deleted.
 * Live releases throw {@link ReleaseLiveException} (409). LLFR-CATALOG-02.3.
 */
@ApplicationScoped
public class DeleteReleaseService implements DeleteRelease {

  private final CatalogRepository repo;

  @Inject
  public DeleteReleaseService(CatalogRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional
  public void delete(ReleaseId id, ArtistId requestingArtist) {
    var release = repo.findRelease(id)
        .orElseThrow(() -> new ReleaseNotFoundException(id.value()));
    if (!release.getArtistId().equals(requestingArtist.value())) {
      throw new UnauthorizedException("Not your release");
    }
    if (release.getStatus() == ReleaseStatus.live) {
      throw new ReleaseLiveException(id.value());
    }
    repo.deleteRelease(id);
  }
}
