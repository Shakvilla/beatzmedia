package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogCountsView;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedCatalogView;

/**
 * Response DTO for {@code GET /admin/catalog}: {@code { items, page, size, total, counts } }.
 * Admin ADD §6 (LLFR-ADMIN-03.1).
 */
public record PagedCatalogDto(
    List<CatalogItemDto> items, int page, int size, long total, CatalogCountsDto counts) {

  public static PagedCatalogDto from(PagedCatalogView view) {
    return new PagedCatalogDto(
        view.items().stream().map(CatalogItemDto::from).toList(),
        view.page(),
        view.size(),
        view.total(),
        CatalogCountsDto.from(view.counts()));
  }

  /** Matches {@code CATALOG_COUNTS} in {@code admin-data.ts}: {@code { pending, published, takedown } }. */
  public record CatalogCountsDto(long pending, long published, long takedown) {
    static CatalogCountsDto from(CatalogCountsView view) {
      return new CatalogCountsDto(view.pending(), view.published(), view.takedown());
    }
  }
}
