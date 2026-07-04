package org.shakvilla.beatzmedia.podcasts.application.port.in;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.podcasts.domain.PodcastId;
import org.shakvilla.beatzmedia.podcasts.domain.TipResult;

/**
 * Input port: a fan tips a podcast show, credited 90/10 via payments (LLFR-PODCAST-02.1). ADD §4.1.
 *
 * <p>This port owns ONLY the podcast-side concerns: it resolves the show and its owning creator
 * server-side, runs the tippability + self-tip guards, then delegates the money movement to payments'
 * tip pipeline through {@code IssueTipUseCase}. It NEVER re-implements the split, the ledger, or
 * idempotency — those live in WU-PAY-3. The 90/10 split (OQ-2 default 10% platform fee from
 * {@code PlatformSettings.tipFeePct}) is posted on settlement (INV-1/INV-4/INV-6).
 *
 * <p><strong>Guards (server-side, never client-trusted):</strong>
 * <ul>
 *   <li>the {@code fan} is the authenticated caller (JWT subject), passed by the resource;
 *   <li>the recipient creator is resolved from the podcast's {@code creator_account_id} — a
 *       client-supplied recipient id is impossible here;
 *   <li>a fan cannot tip a show they own/created ({@code fan != creator}) → {@code SELF_TIP_NOT_ALLOWED};
 *   <li>the show must exist ({@code NOT_FOUND}) and accept tips ({@code TIPS_DISABLED});
 *   <li>the amount must be positive and within the platform charge ceiling ({@code VALIDATION} /
 *       {@code CHARGE_AMOUNT_EXCEEDED}).
 * </ul>
 */
public interface TipShow {

  /**
   * @param id the show being tipped
   * @param fan the authenticated tipping fan (audit actor, INV-10)
   * @param amount the gross tip amount (positive minor units, INV-11)
   * @param method the payment method the tip charges (rail + token); resolved from the request
   * @param idempotencyKey same key ⇒ same {@link TipResult}, no double charge (INV)
   */
  TipResult tip(PodcastId id, AccountId fan, Money amount, TipMethod method, String idempotencyKey);
}
