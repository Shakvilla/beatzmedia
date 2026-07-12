package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Response of {@code GET /admin/catalog}: {@code { items, page, size, total, counts }}. Admin ADD
 * §6 (LLFR-ADMIN-03.1).
 */
public record PagedCatalogView(
    List<CatalogItemRowView> items, int page, int size, long total, CatalogCountsView counts) {}
