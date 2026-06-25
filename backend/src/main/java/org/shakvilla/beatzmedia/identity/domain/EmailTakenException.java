package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a signup or invite uses an email address already registered. Maps to HTTP 409
 * EMAIL_TAKEN. Identity ADD §9.
 */
public class EmailTakenException extends DomainException {

  public EmailTakenException() {
    super(ErrorCode.EMAIL_TAKEN, "An account with this email address already exists.", "email");
  }
}
