package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.Optional;

/**
 * Input port: {@code POST /admin/catalog/:id/flag}. Auth: super-admin, moderator. Unlike {@code
 * approve}/{@code takedown}, {@code flag} is NOT a {@code catalog.domain.Release} FSM transition
 * (there is no "flagged" release status, and {@code PublishRelease}'s own javadoc lists only
 * approve/takedown/reinstate) — it creates a {@code ModerationCase} targeting the release, tying
 * catalog moderation to the moderation queue. This use case therefore DOES own INV-10's
 * AuditEntry for this action (admin's own aggregate, not a catalog mutation — no double-audit
 * conflict with {@link ApproveCatalogItem}/{@link TakedownCatalogItem}). See admin ADD §13
 * (WU-ADM-3 as-built) for the full design-decision write-up. Admin ADD §4.1 (LLFR-ADMIN-03.2).
 *
 * @throws org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException (404)
 */
public interface FlagCatalogItem {

  CatalogItemDetailView flag(String actorId, String releaseId, Optional<String> note);
}
