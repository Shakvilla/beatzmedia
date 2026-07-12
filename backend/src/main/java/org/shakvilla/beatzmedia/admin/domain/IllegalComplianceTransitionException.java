package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link ComplianceRequest} action ({@code start}/{@code complete}) is attempted from a
 * state that does not permit it (e.g. {@code start} on an already-in-progress/completed request, or
 * {@code complete} on an already-completed one). Maps to HTTP 409 {@code ILLEGAL_TRANSITION} (mirrors
 * {@link IllegalRiskTransitionException}). Admin ADD §9 / LLFR-ADMIN-09.1.
 */
public class IllegalComplianceTransitionException extends DomainException {

  public IllegalComplianceTransitionException(String requestId, String action) {
    super(
        ErrorCode.ILLEGAL_TRANSITION,
        "Cannot apply '" + action + "' to compliance request " + requestId + " in its current state");
  }
}
