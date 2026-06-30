package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.catalog.application.port.in.ListStudioReleases;
import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.PageView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for {@link ListStudioReleases}. Lists the authenticated artist's own
 * releases, optionally filtered by status. LLFR-CATALOG-02.1.
 */
@ApplicationScoped
public class ListStudioReleasesService implements ListStudioReleases {

  private final CatalogRepository repo;

  @Inject
  public ListStudioReleasesService(CatalogRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional
  public PageView<StudioReleaseView> list(
      ArtistId owner, Optional<ReleaseStatus> status, int page, int size) {
    Page<Release> raw = repo.releasesByArtist(owner, status, new PageRequest(page, size));
    var items = raw.items().stream().map(this::toView).toList();
    return new PageView<>(items, raw.page(), raw.size(), raw.total());
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
        0L, // streams not tracked at this level
        MoneyView.ofMinor(0L), // revenue placeholder
        MoneyView.ofMinor(r.getListPriceMinor()));
  }
}
