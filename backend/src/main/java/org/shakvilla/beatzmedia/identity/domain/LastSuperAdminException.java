package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an operation would remove or demote the last super-admin, violating the last-
 * super-admin guard invariant. Maps to HTTP 409 LAST_SUPER_ADMIN. Identity ADD §3 /
 * LLFR-IDENTITY-03.3.
 */
public class LastSuperAdminException extends ConflictException {

  public LastSuperAdminException() {
    super(ErrorCode.LAST_SUPER_ADMIN,
        "Cannot remove or demote the last super-admin; at least one must remain");
  }
}
