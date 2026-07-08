package org.shakvilla.beatzmedia.events.domain;

import org.shakvilla.beatzmedia.platform.domain.DomainException;
import org.shakvilla.beatzmedia.platform.domain.ErrorCode;

/**
 * Thrown when an issuance would push a tier's {@code sold} count past its {@code capacity}
 * (INV-EVT-1, no oversell). Maps to 409 {@code TIER_SOLD_OUT}. Events ADD §7 / §9 / OQ-11.
 */
public class TierSoldOutException extends DomainException {

  public TierSoldOutException(String tierId) {
    super(ErrorCode.TIER_SOLD_OUT, "Ticket tier is sold out: " + tierId);
  }
}
