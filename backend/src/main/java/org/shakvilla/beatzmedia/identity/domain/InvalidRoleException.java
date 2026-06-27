package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.ErrorCode;
import org.shakvilla.beatzmedia.platform.domain.ValidationException;

/**
 * Thrown when an unrecognised admin role string is supplied. Maps to HTTP 422 INVALID_ROLE.
 * Identity ADD §3 / LLFR-IDENTITY-03.2.
 */
public class InvalidRoleException extends ValidationException {

  public InvalidRoleException(String message) {
    super(ErrorCode.INVALID_ROLE, message, "role");
  }
}
