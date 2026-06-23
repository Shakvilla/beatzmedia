package org.shakvilla.beatzmedia.platform.domain;

/** Thrown when the platform is in maintenance mode and a write is attempted. Maps to HTTP 503. */
public class MaintenanceModeException extends DomainException {

  public MaintenanceModeException() {
    super(ErrorCode.MAINTENANCE, "The platform is currently undergoing maintenance. Please try again later.");
  }
}
