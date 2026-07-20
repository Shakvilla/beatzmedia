package org.shakvilla.beatzmedia.catalog.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/** Thrown when a split-invite token does not resolve. Maps to 404 SPLIT_INVITE_NOT_FOUND. */
public class SplitInviteNotFoundException extends DomainException {
  public SplitInviteNotFoundException() {
    super(ErrorCode.SPLIT_INVITE_NOT_FOUND, "Split invite not found");
  }
}
