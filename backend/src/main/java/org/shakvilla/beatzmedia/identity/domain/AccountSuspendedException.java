package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a suspended or banned account attempts to authenticate. Maps to HTTP 403
 * ACCOUNT_SUSPENDED. Identity ADD §9.
 */
public class AccountSuspendedException extends DomainException {

  public AccountSuspendedException() {
    super(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended.");
  }
}
