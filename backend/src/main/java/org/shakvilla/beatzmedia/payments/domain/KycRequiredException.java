package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when a withdrawal is requested by a creator whose KYC is not verified (INV-8). Maps to 403
 * {@code KYC_REQUIRED} — the creator must complete KYC before cashing out. Payments ADD §8.
 */
public class KycRequiredException extends DomainException {

  public KycRequiredException(AccountId creator) {
    super(
        ErrorCode.KYC_REQUIRED,
        "KYC verification is required before requesting a withdrawal (account " + creator + ")");
  }
}
