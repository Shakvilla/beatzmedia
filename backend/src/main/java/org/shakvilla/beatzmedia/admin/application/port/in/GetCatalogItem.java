package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: {@code GET /admin/catalog/:id}. Admin ADD §4.1 (LLFR-ADMIN-03.1).
 *
 * @throws org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException (404) if the release
 *     does not exist — reused directly from {@code catalog.domain}, same precedent as {@code
 *     GetUserService} reusing {@code identity.domain.AccountNotFoundException} (WU-ADM-2).
 */
public interface GetCatalogItem {

  CatalogItemDetailView get(String releaseId);
}
