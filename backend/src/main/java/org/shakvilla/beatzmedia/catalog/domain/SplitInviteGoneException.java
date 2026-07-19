package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a split invite is already consumed or expired. Maps to 410 SPLIT_INVITE_GONE. */
public class SplitInviteGoneException extends DomainException {
  public SplitInviteGoneException(String message) {
    super(ErrorCode.SPLIT_INVITE_GONE, message);
  }
}
