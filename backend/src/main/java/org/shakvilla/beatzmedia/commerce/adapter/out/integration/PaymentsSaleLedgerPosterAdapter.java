package org.shakvilla.beatzmedia.commerce.adapter.out.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
 * <p>Idempotent under re-delivery: payments' {@code claimPosting} makes a duplicate settlement for the
 * same source ref a {@link DuplicatePostingException}, which this adapter swallows as a benign no-op
 * (the credit already landed exactly once). This is defence-in-depth alongside commerce's own
 * per-order grant claim, which already short-circuits a re-delivered settlement before this call.
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
      LOG.debugf("sale split for ref %s already posted; ignoring duplicate", refId);
    }
  }
}
