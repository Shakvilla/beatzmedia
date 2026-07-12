package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link RiskSignal} lookup by id finds nothing. Maps to HTTP 404 {@code NOT_FOUND}
 * via the shared {@code DomainExceptionMapper} (mirrors {@link ModerationCaseNotFoundException}).
 * Admin ADD §9 / LLFR-ADMIN-07.1.
 */
public class RiskSignalNotFoundException extends DomainException {

  public RiskSignalNotFoundException(String id) {
    super(ErrorCode.NOT_FOUND, "Risk signal not found: " + id);
  }
}
