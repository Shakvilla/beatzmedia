package org.shakvilla.beatzmedia.catalog.application.port.in;

import java.time.Instant;
import java.util.Optional;

import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;

/**
 * Input port: drives the {@code Release} lifecycle FSM (LLFR-CATALOG-02.5). Invoked by admin
 * moderation endpoints (approve/takedown/reinstate) and by the go-live scheduler job
 * ({@link org.shakvilla.beatzmedia.catalog.adapter.in.job.GoLiveJob}). Catalog ADD §4.1.
 *
 * <p>Authorization: {@code APPROVE_IMMEDIATE} / {@code APPROVE_SCHEDULED} / {@code TAKEDOWN} /
 * {@code REINSTATE} require the admin {@code moderator}/{@code editor} scope, enforced at the
 * inbound REST adapter and re-checked here is not required (RBAC is role-based, not
 * per-resource-owner, for admin actions). {@code GO_LIVE} is system-only (scheduler); it is never
 * exposed on an HTTP path.
 *
 * <p>Idempotency: FSM transitions are guard-idempotent — re-issuing a transition that no longer
 * applies (e.g. approving an already-live release) throws {@code IllegalTransitionException}
 * (409 {@code ILLEGAL_TRANSITION}) rather than silently repeating a side effect.
 */
public interface PublishRelease {

  /**
   * Applies the given transition to the release identified by {@code id}.
   *
   * @param id the release to transition
   * @param action which FSM edge to apply
   * @param actorId the acting admin's account id (ignored for {@code GO_LIVE}, which is
   *     system-initiated); used for the {@code AuditEntry} actor field (INV-10)
   * @param scheduledAt required (and must be strictly future) for {@code APPROVE_SCHEDULED};
   *     ignored for all other actions
   * @throws org.shakvilla.beatzmedia.catalog.domain.IllegalTransitionException on any illegal edge
   */
  StudioReleaseView transition(
      ReleaseId id, ReleaseTransition action, String actorId, Optional<Instant> scheduledAt);

  /**
   * Overload for {@code TAKEDOWN}, which the contract requires a free-text {@code reason} for.
   * The reason is carried on the {@code AuditEntry} and the {@code ContentTakenDown} event.
   */
  default StudioReleaseView transition(
      ReleaseId id,
      ReleaseTransition action,
      String actorId,
      Optional<Instant> scheduledAt,
      String reason) {
    return transition(id, action, actorId, scheduledAt);
  }

  enum ReleaseTransition {
    APPROVE_IMMEDIATE,
    APPROVE_SCHEDULED,
    GO_LIVE,
    TAKEDOWN,
    REINSTATE
  }
}
