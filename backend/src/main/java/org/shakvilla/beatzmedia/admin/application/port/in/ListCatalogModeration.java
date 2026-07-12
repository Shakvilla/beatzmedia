package org.shakvilla.beatzmedia.admin.application.port.in;

import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/** Input port: {@code GET /admin/catalog}. Admin ADD §4.1 (LLFR-ADMIN-03.1). */
public interface ListCatalogModeration {

  PagedCatalogView list(CatalogQuery query, PageRequest page);
}
