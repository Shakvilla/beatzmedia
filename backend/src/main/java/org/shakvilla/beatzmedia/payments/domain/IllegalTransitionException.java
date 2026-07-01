package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a {@link PaymentIntent} lifecycle transition is attempted from a status that does not
 * permit it (e.g. settling a {@code failed} intent). Maps to 409 {@code ILLEGAL_TRANSITION}.
 * Payments ADD §8.
 */
public class IllegalTransitionException extends DomainException {

  public IllegalTransitionException(String message) {
    super(ErrorCode.ILLEGAL_TRANSITION, message);
  }
}
