package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when an operation targets an account id that does not exist. Maps to HTTP 404. */
public class AccountNotFoundException extends DomainException {

  public AccountNotFoundException(String accountId) {
    super(ErrorCode.NOT_FOUND, "Account not found: " + accountId);
  }
}
