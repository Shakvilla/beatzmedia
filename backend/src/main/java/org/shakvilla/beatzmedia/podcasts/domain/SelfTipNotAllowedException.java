package org.shakvilla.beatzmedia.podcasts.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when the authenticated fan resolves to the same account as the show's creator — a creator
 * cannot tip their own show (the self-tip guard, carried over from the WU-PAY-3 / WU-COM-2 security
 * reviews). Rejecting server-side prevents a self-directed 90/10 money round-trip and the associated
 * fee leakage. Maps to HTTP 422 / {@code SELF_TIP_NOT_ALLOWED} (a mapped 4xx, never an unmapped 500).
 * Podcasts ADD §9.
 */
public class SelfTipNotAllowedException extends DomainException {

  public SelfTipNotAllowedException() {
    super(ErrorCode.SELF_TIP_NOT_ALLOWED, "A creator cannot tip their own show");
  }
}
