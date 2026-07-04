package org.shakvilla.beatzmedia.payments.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an admin payout run attempts to execute a payout for a creator whose KYC is not
 * verified (INV-8). A single send blocks on KYC (API-CONTRACT §Finance); a weekly run skips
 * unverified creators rather than throwing. Maps to 409 {@code KYC_BLOCKED}. Payments ADD §8.
 */
public class KycBlockedException extends DomainException {

  public KycBlockedException(AccountId creator) {
    super(
        ErrorCode.KYC_BLOCKED,
        "payout blocked: creator " + creator + " is not KYC-verified (INV-8)");
  }
}
