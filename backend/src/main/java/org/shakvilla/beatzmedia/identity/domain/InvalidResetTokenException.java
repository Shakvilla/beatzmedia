package org.shakvilla.beatzmedia.identity.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a password-reset token cannot be redeemed. Maps to HTTP 410 GONE.
 *
 * <p>Raised identically whether the token is unknown, already used, or expired. The caller must not
 * be able to tell the difference: distinguishing "never existed" from "already spent" would let
 * someone probe harvested tokens for validity, and the message is the same advice either way —
 * request a new link.
 */
public class InvalidResetTokenException extends DomainException {

  public InvalidResetTokenException() {
    super(
        ErrorCode.RESET_TOKEN_INVALID,
        "This password reset link is no longer valid. Please request a new one.");
  }
}
