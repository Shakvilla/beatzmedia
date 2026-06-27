package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when input fails domain-level validation. Maps to HTTP 422. */
public class ValidationException extends DomainException {

  public ValidationException(String message) {
    super(ErrorCode.VALIDATION, message);
  }

  public ValidationException(String message, String field) {
    super(ErrorCode.VALIDATION, message, field);
  }

  /** Constructor for subclasses that carry a specific error code (e.g. INVALID_ROLE). */
  protected ValidationException(ErrorCode code, String message, String field) {
    super(code, message, field);
  }
}
