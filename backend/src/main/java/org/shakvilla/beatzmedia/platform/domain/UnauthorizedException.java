package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when an authenticated principal lacks the required role/scope. Maps to HTTP 403. */
public class UnauthorizedException extends DomainException {

  public UnauthorizedException(String message) {
    super(ErrorCode.UNAUTHORIZED, message);
  }
}
