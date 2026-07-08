package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when a request conflicts with existing state. Maps to HTTP 409. */
public class ConflictException extends DomainException {

  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }

  public ConflictException(ErrorCode code, String message) {
    super(code, message);
  }

  /** Constructor for subclasses that carry a field pointer (e.g. {@code USERNAME_TAKEN}). */
  protected ConflictException(ErrorCode code, String message, String field) {
    super(code, message, field);
  }
}
