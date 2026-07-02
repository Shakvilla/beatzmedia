package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.IdempotencyKey;
import org.shakvilla.beatzmedia.payments.domain.PaymentMethodRef;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Input port for a fan tipping a creator (LLFR-PAYMENTS-05 / 02.1). Unlike a sale, the recipient
 * creator is known directly (a direct parameter), so the 90/10 split can be posted to that creator's
 * {@code creator_payable} account on settlement.
 *
 * <p>A tip is backed by a {@code payment_intent} exactly like a purchase (same idempotency &
 * advisory-lock mechanism as {@link InitiateCharge}): {@code idempotency_key} UNIQUE makes a duplicate
 * tip a no-op replay (one effect per key). No value moves until settlement (INV-1); on settlement the
 * split is posted to the ledger (INV-4/INV-6) and a {@code TipReceived} domain event is emitted.
 */
public interface IssueTip {

  /**
   * Initiate a tip charge from {@code fan} to {@code creator}. Returns the created/settled tip view.
   *
   * @param fan the authenticated tipping fan (audit actor, INV-10)
   * @param creator the recipient creator (the 90% share goes here)
   * @param amount the gross tip amount (positive minor units)
   * @param method the payment rail/method
   * @param key the idempotency key (same key ⇒ same tip, no double charge)
   */
  TipView tip(
      AccountId fan,
      AccountId creator,
      Money amount,
      PaymentMethodRef method,
      IdempotencyKey key);
}
