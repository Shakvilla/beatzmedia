package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when input fails domain-level validation. Maps to HTTP 422. */
public class ValidationException extends DomainException {

  public ValidationException(String message) {
    super(ErrorCode.VALIDATION, message);
  }

  public ValidationException(String message, String field) {
    super(ErrorCode.VALIDATION, message, field);
  }
}
