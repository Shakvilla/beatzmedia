package org.shakvilla.beatzmedia.commerce.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an {@link Order} is asked for an illegal status transition (e.g. {@code failed → paid},
 * which would grant ownership without settlement — INV-1). Maps to HTTP 409 {@code ILLEGAL_TRANSITION}.
 * Commerce ADD §8 / §9.
 */
public class IllegalOrderTransitionException extends ConflictException {

  public IllegalOrderTransitionException(String from, String to) {
    super(ErrorCode.ILLEGAL_TRANSITION, "Illegal order transition: " + from + " -> " + to);
  }
}
