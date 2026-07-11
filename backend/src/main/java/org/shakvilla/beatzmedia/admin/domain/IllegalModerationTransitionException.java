package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link ModerationCase} action ({@code review}/{@code approve}/{@code remove}/
 * {@code escalate}/{@code dismiss}) is attempted on a case whose current state does not permit it
 * (e.g. any action on an already-{@code resolved} case, or a repeat {@code escalate}). Maps to
 * HTTP 409 {@code ILLEGAL_TRANSITION}. Admin ADD §9 / LLFR-ADMIN-04.1.
 */
public class IllegalModerationTransitionException extends DomainException {

  public IllegalModerationTransitionException(String caseId, String action) {
    super(
        ErrorCode.ILLEGAL_TRANSITION,
        "Cannot apply '" + action + "' to moderation case " + caseId + " in its current state");
  }
}
