package org.shakvilla.beatzmedia.admin.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link ComplianceRequest} lookup by id finds nothing. Maps to HTTP 404 {@code
 * NOT_FOUND} via the shared {@code DomainExceptionMapper} (mirrors {@link
 * RiskSignalNotFoundException}). Admin ADD §9 / LLFR-ADMIN-09.1.
 */
public class ComplianceRequestNotFoundException extends DomainException {

  public ComplianceRequestNotFoundException(String id) {
    super(ErrorCode.NOT_FOUND, "Compliance request not found: " + id);
  }
}
