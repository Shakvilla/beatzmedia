package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: {@code POST /admin/catalog/:id/reinstate}. Auth: super-admin, moderator. Not in
 * admin ADD §5.1's illustrative REST table (which lists only approve/flag/takedown), but relocated
 * here from catalog's temporary {@code AdminCatalogResource} placeholder alongside approve/
 * takedown — catalog ADD §5.1's own WU-CAT-4 note explicitly earmarked this endpoint ("these three
 * endpoints") for relocation into the `admin` module once it existed. Drives the {@code
 * takedown -> live} FSM edge via catalog's real, self-auditing {@code PublishRelease}. Admin ADD
 * §4.1 (additive to LLFR-ADMIN-03.2, catalog ADD LLFR-CATALOG-02.5).
 *
 * @throws org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException (404)
 * @throws org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException (409)
 */
public interface ReinstateCatalogItem {

  CatalogItemDetailView reinstate(String actorId, String releaseId);
}
