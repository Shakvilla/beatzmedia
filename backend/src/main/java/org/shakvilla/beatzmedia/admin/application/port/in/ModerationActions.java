package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.Optional;

/**
 * Input port bundling the five moderation-queue actions (admin ADD §4.1's own sketch groups them
 * into one interface, unlike catalog moderation's per-action interfaces). Auth: super-admin,
 * moderator. Each action appends exactly one {@code AuditEntry} (INV-10, {@code
 * AuditType.MODERATION}) — this is admin's own aggregate, so admin owns the audit write (no
 * cross-module self-audit conflict, unlike {@link ApproveCatalogItem}/{@link TakedownCatalogItem}).
 * Admin ADD §4.1 (LLFR-ADMIN-04.1).
 *
 * @throws org.shakvilla.beatzmedia.admin.domain.ModerationCaseNotFoundException (404)
 * @throws org.shakvilla.beatzmedia.admin.domain.IllegalModerationTransitionException (409) on any
 *     action against an already-{@code resolved} case, or a repeat {@code escalate}
 */
public interface ModerationActions {

  ModerationCaseView review(String actorId, String caseId);

  ModerationCaseView approve(String actorId, String caseId);

  /** {@code reason} is optional per admin ADD §5.1's {@code { reason? }} request body. */
  ModerationCaseView remove(String actorId, String caseId, Optional<String> reason);

  ModerationCaseView escalate(String actorId, String caseId);

  ModerationCaseView dismiss(String actorId, String caseId);
}
