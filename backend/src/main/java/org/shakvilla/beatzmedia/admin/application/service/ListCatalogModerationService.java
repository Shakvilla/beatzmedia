package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.ListCatalogModeration;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedCatalogView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for LLFR-ADMIN-03.1 (catalog moderation list). Auth: super-admin,
 * moderator, support (read; enforced by the inbound {@code @RolesAllowed}).
 */
@ApplicationScoped
public class ListCatalogModerationService implements ListCatalogModeration {

  private final CatalogAdminReader catalogAdminReader;

  @Inject
  public ListCatalogModerationService(CatalogAdminReader catalogAdminReader) {
    this.catalogAdminReader = catalogAdminReader;
  }

  @Override
  @Transactional
  public PagedCatalogView list(CatalogQuery query, PageRequest page) {
    Page<CatalogAdminReader.CatalogRow> result =
        catalogAdminReader.list(query.filter(), query.q(), page);
    List<CatalogItemRowView> items =
        result.items().stream().map(CatalogItemMapper::toRowView).toList();
    return new PagedCatalogView(
        items, result.page(), result.size(), result.total(),
        CatalogItemMapper.toCountsView(catalogAdminReader.counts()));
  }
}
