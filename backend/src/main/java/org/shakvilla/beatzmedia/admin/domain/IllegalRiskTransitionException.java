package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link RiskSignal} action ({@code review}/{@code clear}/{@code ban}) is attempted on
 * a signal that is not {@code open} (i.e. already {@code cleared} or {@code banned}). Maps to HTTP
 * 409 {@code ILLEGAL_TRANSITION} (mirrors {@link IllegalModerationTransitionException}). Admin ADD §9
 * / LLFR-ADMIN-07.1.
 */
public class IllegalRiskTransitionException extends DomainException {

  public IllegalRiskTransitionException(String signalId, String action) {
    super(
        ErrorCode.ILLEGAL_TRANSITION,
        "Cannot apply '" + action + "' to risk signal " + signalId + " in its current state");
  }
}
