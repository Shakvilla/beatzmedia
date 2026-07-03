package org.shakvilla.beatzmedia.commerce.application.port.out;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Output port abstracting the cross-module call that posts a settled sale's 70/30 split to the
 * payments double-entry ledger (INV-4/INV-6). The adapter forwards to the payments
 * {@code LedgerPostingService.postSaleSplit} reusable primitive (payments ADD §4 note / WU-PAY-3
 * as-built), so commerce supplies the creator↔order mapping payments could not know, while the
 * ledger balance + exactly-once claim stay entirely inside payments.
 *
 * <p>The split percentage is NOT decided in commerce — payments reads {@code platformFeePct} from
 * {@code PlatformSettings} (INV-11). The posting is idempotent per source ref inside payments (the
 * {@code ledger_posting} claim), so this call is safe under a re-delivered settlement.
 */
public interface SaleLedgerPoster {

  /**
   * Post the 70/30 sale split crediting {@code creator} their share of {@code gross}.
   *
   * @param provider the rail the funds came in on (its clearing account is debited)
   * @param creator the recipient creator account (payable credited the creator share)
   * @param gross the settled gross amount for this creator's lines (positive minor units)
   * @param refId the source reference traced onto every ledger row (the payment-intent id)
   */
  void postSaleSplit(String provider, AccountId creator, Money gross, String refId);
}
