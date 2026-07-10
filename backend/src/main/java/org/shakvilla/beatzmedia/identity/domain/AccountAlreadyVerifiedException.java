package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.ConflictException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when {@link Account#verifyArtist(java.time.Instant)} is called on an account that is
 * already verified. Maps to HTTP 409 ALREADY_VERIFIED. Identity ADD §3 / LLFR-ADMIN-02.2.
 */
public class AccountAlreadyVerifiedException extends ConflictException {

  public AccountAlreadyVerifiedException() {
    super(ErrorCode.ALREADY_VERIFIED, "Account is already verified");
  }
}
