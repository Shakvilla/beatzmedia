package org.shakvilla.beatzmedia.podcasts.application.port.out;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.application.port.in.TipMethod;

/**
 * Output port through which {@code TipShow} delegates the actual money movement to the payments
 * module (WU-POD-2). The adapter ({@code PaymentsTipAdapter}) wraps payments' {@code IssueTip} INPUT
 * port — podcasts NEVER reads or writes payments tables and NEVER re-implements the split/ledger; the
 * 90/10 tip split (creator 90 / platform 10, from {@code PlatformSettings.tipFeePct}, OQ-2 default
 * 10%) is posted by the payments settlement machinery on settlement (INV-1/INV-4/INV-6). ADD §4.2.
 *
 * <p>The recipient {@code creator} is resolved server-side by {@code TipShow} from the podcast's
 * {@code creator_account_id} — it is NEVER supplied by the client. Idempotency (INV / one effect per
 * key) is enforced inside payments, backed by the {@code idempotency_key} UNIQUE constraint, so a
 * key replay returns the same tip with no double charge.
 */
public interface IssueTipUseCase {

  /**
   * Initiate a tip charge from {@code fan} to {@code creator} for {@code amount}, reusing the
   * payments idempotency mechanism.
   *
   * @param fan the authenticated tipping fan (audit actor, INV-10)
   * @param creator the server-resolved recipient creator (the 90% share)
   * @param amount the gross tip amount (positive minor units, INV-11)
   * @param method the payment instrument the tip charges (rail + token)
   * @param idempotencyKey same key ⇒ same tip, no double charge
   * @return the tip outcome (id + coarse status)
   */
  TipOutcome issueTip(
      AccountId fan, AccountId creator, Money amount, TipMethod method, String idempotencyKey);
}
