package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a release-lifecycle transition is attempted from a status that does not permit it
 * (e.g. {@code draft -> live} directly). Maps to 409 {@code ILLEGAL_TRANSITION}. Catalog ADD §3 /
 * LLFR-CATALOG-02.5.
 */
public class IllegalTransitionException extends DomainException {

  public IllegalTransitionException(ReleaseStatus from, String action) {
    super(
        ErrorCode.ILLEGAL_TRANSITION,
        "Cannot apply '" + action + "' to a release in status '" + from + "'");
  }

  public IllegalTransitionException(String message) {
    super(ErrorCode.ILLEGAL_TRANSITION, message);
  }
}
