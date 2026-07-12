package org.shakvilla.beatzmedia.admin.application.port.in;

import java.time.Instant;
import java.util.Optional;

/**
 * Input port: {@code POST /admin/catalog/:id/approve}. Auth: super-admin, moderator. Drives the
 * release -> {@code scheduled}/{@code live} transition via catalog's real {@code PublishRelease}
 * FSM, which self-audits (AuditType.MODERATION) — this use case does NOT append a second
 * AuditEntry (INV-10 "exactly one"). Admin ADD §4.1 (LLFR-ADMIN-03.2).
 *
 * @throws org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException (404)
 * @throws org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException (409)
 */
public interface ApproveCatalogItem {

  CatalogItemDetailView approve(String actorId, String releaseId, Optional<Instant> goLiveAt);
}
