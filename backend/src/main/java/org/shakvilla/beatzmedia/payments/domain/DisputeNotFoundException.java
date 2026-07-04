package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an admin references a dispute id that does not exist. Maps to 404 {@code
 * DISPUTE_NOT_FOUND} (payments ADD §5.1, LLFR-PAYMENTS-04.1/04.2/04.3).
 */
public class DisputeNotFoundException extends DomainException {

  public DisputeNotFoundException(DisputeId id) {
    super(ErrorCode.DISPUTE_NOT_FOUND, "dispute not found: " + id);
  }
}
