package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the supplied raw password does not meet minimum strength requirements (< 8 chars).
 * Maps to HTTP 422 WEAK_PASSWORD. Identity ADD §9 / LLFR-IDENTITY-01.1.
 */
public class WeakPasswordException extends DomainException {

  public WeakPasswordException() {
    super(ErrorCode.WEAK_PASSWORD, "Password must be at least 8 characters.", "password");
  }
}
