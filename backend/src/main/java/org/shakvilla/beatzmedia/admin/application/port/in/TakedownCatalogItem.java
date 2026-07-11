package org.shakvilla.beatzmedia.admin.application.port.in;

/**
 * Input port: {@code POST /admin/catalog/:id/takedown { reason }}. Auth: super-admin, moderator.
 * {@code reason} is required (422 {@code VALIDATION} via {@code @NotBlank} at the REST boundary —
 * same "reuse the generic code" convention as {@code SuspendUser}). Drives catalog's real {@code
 * PublishRelease} FSM (self-audited, AuditType.MODERATION — this use case does NOT append a second
 * AuditEntry). Admin ADD §4.1 (LLFR-ADMIN-03.2).
 *
 * @throws org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException (404)
 * @throws org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException (409)
 */
public interface TakedownCatalogItem {

  CatalogItemDetailView takedown(String actorId, String releaseId, String reason);
}
