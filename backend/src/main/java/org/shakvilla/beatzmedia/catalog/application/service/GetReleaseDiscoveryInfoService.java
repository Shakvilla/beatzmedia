package org.shakvilla.beatzmedia.catalog.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.GetReleaseDiscoveryInfo;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;

@ApplicationScoped
@Transactional(Transactional.TxType.SUPPORTS)
public class GetReleaseDiscoveryInfoService implements GetReleaseDiscoveryInfo {

  private final CatalogRepository catalog;

  @Inject
  public GetReleaseDiscoveryInfoService(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  @Override
  public ReleaseDiscoveryInfo get(ReleaseId releaseId) {
    var release =
        catalog.findRelease(releaseId)
            .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    var artist =
        catalog.findArtist(new ArtistId(release.getArtistId()))
            .orElseThrow(() -> new ReleaseNotFoundException(releaseId.value()));
    return new ReleaseDiscoveryInfo(
        release.getId(),
        release.getArtistId(),
        release.getTitle(),
        artist.getName(),
        release.getCoverImage(),
        release.getType().name(),
        release.getWentLiveAt() == null ? null : release.getWentLiveAt().toString());
  }
}
