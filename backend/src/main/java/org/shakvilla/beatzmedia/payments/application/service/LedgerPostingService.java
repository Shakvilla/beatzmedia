package org.shakvilla.beatzmedia.payments.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.RevenueSplit;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Shared application collaborator that posts a settled sale or tip split to the double-entry ledger
 * (payments ADD §3/§8, INV-4/INV-6). Percentages are NEVER decided here — the caller passes the fee
 * percentage sourced from {@code PlatformSettings} (INV-11). Every posting is balanced by construction
 * (a full debit to the provider-clearing account, a credit of the creator share, a credit of the
 * platform fee) and is verified again by {@link LedgerRepository#postBalanced} and the DB trigger.
 *
 * <p><strong>Sales vs tips — module-decoupling decision (ADR-20 sibling).</strong> The tip path
 * ({@link #postTipSplit}) is fully wired here because {@code IssueTip} knows the recipient creator
 * directly. The <em>sale</em> path ({@link #postSaleSplit}) is implemented as a reusable primitive but
 * is <em>not</em> triggered by the {@code PaymentSettled} event at this WU: that event carries the
 * payer + orderRef but NOT the recipient creator, and the payer↔creator/order mapping is commerce's
 * concern (WU-COM-2). Inventing a fake creator id or reading commerce's tables would violate the
 * module boundary and could unbalance the ledger. WU-COM-2 supplies the creator mapping and calls
 * {@link #postSaleSplit} — so sales splits wire in trivially with no change to the ledger primitives.
 */
@ApplicationScoped
public class LedgerPostingService {

  private final LedgerRepository ledger;
  private final IdGenerator ids;

  @Inject
  public LedgerPostingService(LedgerRepository ledger, IdGenerator ids) {
    this.ledger = ledger;
    this.ids = ids;
  }

  /**
   * Post a settled <strong>sale</strong> split (INV-4): DEBIT provider-clearing the gross, CREDIT the
   * creator's payable the creator share, CREDIT platform-revenue the fee. Balanced by construction.
   * The funds are cleared immediately (a confirmed settlement). Reusable primitive for WU-COM-2.
   *
   * @param provider the rail the funds came in on (its clearing account is debited)
   * @param creator the recipient creator (its payable account is credited the creator share)
   * @param gross the settled gross amount (positive minor units)
   * @param platformFeePct the platform fee percentage from {@code PlatformSettings} (e.g. 30)
   * @param refId the source reference (e.g. the payment-intent id) traced onto every row
   * @param at the settlement/clearing instant
   * @return the resolved split (creator share + platform fee)
   */
  public RevenueSplit postSaleSplit(
      Provider provider,
      AccountId creator,
      Money gross,
      int platformFeePct,
      String refId,
      Instant at) {
    return postSplit("intent", provider, creator, gross, platformFeePct, refId, at);
  }

  /**
   * Post a settled <strong>tip</strong> split (INV-4): DEBIT provider-clearing the gross, CREDIT the
   * creator's payable the creator share ({@code 100 − tipFeePct}%), CREDIT platform-revenue the tip
   * fee. Balanced by construction; cleared immediately.
   *
   * @param tipFeePct the tip fee percentage from {@code PlatformSettings} (e.g. 10)
   */
  public RevenueSplit postTipSplit(
      Provider provider,
      AccountId creator,
      Money gross,
      int tipFeePct,
      String refId,
      Instant at) {
    return postSplit("tip", provider, creator, gross, tipFeePct, refId, at);
  }

  private RevenueSplit postSplit(
      String refType,
      Provider provider,
      AccountId creator,
      Money gross,
      int feePct,
      String refId,
      Instant at) {
    RevenueSplit split = RevenueSplit.ofFeePct(gross, feePct);

    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, provider.name()));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId platformRevenue =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null));

    TxnId txn = new TxnId(ids.newId());

    // DEBIT provider_clearing (gross) = CREDIT creator_payable (share) + CREDIT platform_revenue (fee).
    // Only positive-amount rows are posted, so a ₵0 creator share or ₵0 fee (edge percentages) is
    // simply omitted; the remaining rows still balance because omitting a 0 changes nothing.
    List<LedgerEntry> entries = new java.util.ArrayList<>(3);
    entries.add(
        LedgerEntry.post(
            ids.newId(), txn, providerClearing, Direction.DEBIT, gross, refType, refId, at, at));
    if (split.creatorShare().isPositive()) {
      entries.add(
          LedgerEntry.post(
              ids.newId(), txn, creatorPayable, Direction.CREDIT, split.creatorShare(),
              refType, refId, at, at));
    }
    if (split.platformFee().isPositive()) {
      entries.add(
          LedgerEntry.post(
              ids.newId(), txn, platformRevenue, Direction.CREDIT, split.platformFee(),
              refType, refId, at, at));
    }

    ledger.postBalanced(txn, entries);
    return split;
  }
}
