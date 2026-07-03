package org.shakvilla.beatzmedia.commerce.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.commerce.application.port.out.SaleLedgerPoster;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.application.service.LedgerPostingService;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Outbound integration adapter implementing {@link SaleLedgerPoster} by forwarding to the payments
 * {@code LedgerPostingService.postSaleSplit} reusable primitive (payments ADD §4 note / WU-PAY-3
 * as-built). Commerce supplies the creator↔order mapping payments could not know; the balanced posting,
 * the exactly-once {@code ledger_posting} claim and the split percentage (from {@code PlatformSettings})
 * stay entirely inside payments (INV-4/INV-6/INV-11).
 *
 * <p><strong>Transaction isolation (finding F1).</strong> This method runs {@code REQUIRES_NEW}, in a
 * <em>separate</em> transaction from the caller's ({@code GrantOwnershipService}) settlement→grant
 * transaction. The call crosses the CDI proxy boundary (distinct beans), so the new transaction
 * actually starts. This matters because {@code LedgerPostingService.claimPosting} does an
 * {@code INSERT + flush} on the payments {@code ledger_posting} PK: on a genuine duplicate the DB
 * raises 23505, marking the <em>current</em> transaction rollback-only. Without this boundary that
 * poison would be the caller's grant transaction — rolling back all grants, the paid transition, the
 * cart clear, the order-grant claim, the audit and the event (buyer charged, nothing granted). With
 * {@code REQUIRES_NEW}, only THIS split's transaction rolls back; the {@link DuplicatePostingException}
 * is then caught here as a benign no-op, and the caller's grant transaction is untouched.
 *
 * <p>Idempotency under a re-delivered settlement is normally handled one level up by commerce's
 * order-level {@code order_grant_posting} claim (which returns before any posting), so a
 * {@link DuplicatePostingException} here is only reachable on a genuine same-ref race — defence in
 * depth, not the primary guard. The source {@code refId} is per-creator-unique
 * ({@code paymentIntentId:creatorId}) so distinct creators in one order never collide (F1).
 */
@ApplicationScoped
public class PaymentsSaleLedgerPosterAdapter implements SaleLedgerPoster {

  private static final Logger LOG = Logger.getLogger(PaymentsSaleLedgerPosterAdapter.class);

  private final LedgerPostingService ledgerPostingService;
  private final PlatformSettingsProvider settings;
  private final Clock clock;

  @Inject
  public PaymentsSaleLedgerPosterAdapter(
      LedgerPostingService ledgerPostingService,
      PlatformSettingsProvider settings,
      Clock clock) {
    this.ledgerPostingService = ledgerPostingService;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void postSaleSplit(String provider, AccountId creator, Money gross, String refId) {
    if (!gross.isPositive()) {
      return; // nothing to split (a ₵0 line) — never post an empty txn
    }
    int platformFeePct = settings.current().platformFeePct(); // INV-4/INV-11 — never hard-coded
    try {
      ledgerPostingService.postSaleSplit(
          Provider.fromWire(provider),
          new org.shakvilla.beatzmedia.payments.domain.AccountId(creator.value()),
          gross,
          platformFeePct,
          refId,
          clock.now());
    } catch (DuplicatePostingException e) {
      // A genuine same-ref duplicate: THIS REQUIRES_NEW transaction rolled back in isolation; the
      // credit already landed exactly once. Swallow as a benign no-op (the caller's grant txn is safe).
      LOG.debugf("sale split for ref %s already posted; ignoring duplicate", refId);
    }
  }
}
