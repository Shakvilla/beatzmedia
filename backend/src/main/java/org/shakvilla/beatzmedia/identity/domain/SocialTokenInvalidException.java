package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a social provider token fails verification (invalid, expired, or an unrecognised
 * provider). Maps to HTTP 401 SOCIAL_TOKEN_INVALID. Identity ADD §9 / LLFR-IDENTITY-01.3.
 */
public class SocialTokenInvalidException extends DomainException {

  public SocialTokenInvalidException() {
    super(ErrorCode.SOCIAL_TOKEN_INVALID, "The social login token could not be verified.");
  }
}
