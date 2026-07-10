package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown by the admin-facing suspend use case when the target account is already suspended.
 * {@link Account#suspend(java.time.Instant)} itself has no already-suspended guard (it is a plain
 * status setter reused elsewhere); the guard lives in the calling service instead. Maps to HTTP
 * 409 ALREADY_SUSPENDED. Identity ADD §3 / LLFR-ADMIN-02.3.
 */
public class AccountAlreadySuspendedException extends ConflictException {

  public AccountAlreadySuspendedException() {
    super(ErrorCode.ALREADY_SUSPENDED, "Account is already suspended");
  }
}
