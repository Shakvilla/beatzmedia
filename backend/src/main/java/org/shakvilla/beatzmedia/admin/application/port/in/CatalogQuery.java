package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.admin.domain.CatalogFilter;

/**
 * Query parameters for {@code GET /admin/catalog?status=&q=}. Admin ADD §5.1 (LLFR-ADMIN-03.1).
 */
public record CatalogQuery(CatalogFilter filter, String q) {}
