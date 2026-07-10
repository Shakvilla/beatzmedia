package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@link Account#reactivate(java.time.Instant)} is called on an account that is not
 * currently suspended. Maps to HTTP 409 NOT_SUSPENDED. Identity ADD §3 / LLFR-ADMIN-02.4.
 */
public class AccountNotSuspendedException extends ConflictException {

  public AccountNotSuspendedException() {
    super(ErrorCode.NOT_SUSPENDED, "Only suspended accounts can be reactivated");
  }
}
