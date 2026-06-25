package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when login credentials are invalid — either the email is unknown or the password does not
 * match. The message is intentionally identical for both cases to avoid enumeration of registered
 * emails. Maps to HTTP 401 INVALID_CREDENTIALS. Identity ADD §9 / DoD §12.2.
 */
public class InvalidCredentialsException extends DomainException {

  private static final String NON_ENUMERATING_MESSAGE =
      "The email address or password is incorrect.";

  public InvalidCredentialsException() {
    super(ErrorCode.INVALID_CREDENTIALS, NON_ENUMERATING_MESSAGE);
  }
}
