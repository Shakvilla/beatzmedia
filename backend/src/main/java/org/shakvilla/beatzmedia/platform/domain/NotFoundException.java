package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class NotFoundException extends DomainException {

  public NotFoundException(String message) {
    super(ErrorCode.NOT_FOUND, message);
  }
}
