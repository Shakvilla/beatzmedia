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
 * {@code ledger_posting} claim on {@code (ref_type, ref_id)}).
 *
 * <p><strong>Per-creator source ref (finding F1).</strong> The payments {@code ledger_posting} PK is
 * {@code (ref_type, ref_id)}, so an order that credits ≥2 distinct creators must pass a
 * <em>per-creator-unique</em> {@code refId} — otherwise the second creator's claim collides on the PK
 * (23505), and because the claim runs in the caller's transaction, the whole settlement→grant unit
 * would roll back (buyer charged, nothing granted). The caller therefore composes {@code refId} as
 * {@code paymentIntentId + ":" + creatorAccountId} and posts ONE aggregated split per creator.
 * Idempotency across re-deliveries is guaranteed one level up by commerce's order-level
 * {@code order_grant_posting} claim (a re-delivered settlement returns before any posting), so the
 * per-creator ref only needs uniqueness within a single grant pass.
 */
public interface SaleLedgerPoster {

  /**
   * Post the 70/30 sale split crediting {@code creator} their share of {@code gross}. Runs in its own
   * {@code REQUIRES_NEW} transaction so a genuine duplicate claim cannot poison the caller's
   * settlement→grant transaction (finding F1).
   *
   * @param provider the rail the funds came in on (its clearing account is debited)
   * @param creator the recipient creator account (payable credited the creator share)
   * @param gross the settled gross amount aggregated for this creator's lines (positive minor units)
   * @param refId the <strong>per-creator-unique</strong> source ref traced onto every ledger row —
   *     {@code paymentIntentId + ":" + creatorAccountId} (see class doc / finding F1)
   */
  void postSaleSplit(String provider, AccountId creator, Money gross, String refId);
}
