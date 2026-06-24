package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when arithmetic is attempted on Money values with different currencies. */
public class MismatchedCurrencyException extends DomainException {

  public MismatchedCurrencyException(String message) {
    super(ErrorCode.VALIDATION, message);
  }
}
