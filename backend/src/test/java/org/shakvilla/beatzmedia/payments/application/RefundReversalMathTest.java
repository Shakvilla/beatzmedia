package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.payments.application.port.out.DuplicatePostingException;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountId;
import org.shakvilla.beatzmedia.payments.domain.LedgerAccountKind;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.TxnId;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for the refund clawback reversal (INV-6/INV-9) against the in-memory ledger fake, which
 * enforces the same balance assertion as the real adapter. Proves the reversal is a balanced mirror of
 * the original split, claws back the creator credit + platform fee, drives the creator's available
 * NEGATIVE (owed) when the credit was already withdrawn, and is exactly-once.
 */
@Tag("unit")
class RefundReversalMathTest {

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  /** Post a ₵10 sale split (creator 700 / platform 300) for an intent, as WU-COM-2/PAY-3 would. */
  private static void postSale(FakeLedgerRepository ledger, String intentId, AccountId creator) {
    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn"));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId platformRevenue =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null));
    TxnId txn = new TxnId("sale-" + intentId);
    Instant now = Instant.now();
    ledger.claimPosting(txn, "intent", intentId);
    ledger.postBalanced(
        txn,
        List.of(
            LedgerEntry.post("s1", txn, providerClearing, Direction.DEBIT, ghs(1000), "intent", intentId, now, now),
            LedgerEntry.post("s2", txn, creatorPayable, Direction.CREDIT, ghs(700), "intent", intentId, now, now),
            LedgerEntry.post("s3", txn, platformRevenue, Direction.CREDIT, ghs(300), "intent", intentId, now, now)));
  }

  @Test
  void refund_reverses_the_sale_split_balanced_and_claws_back_the_creator_credit() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("creator-1");
    postSale(ledger, "intent-1", creator);
    assertEquals(700, ledger.balanceOf(creator).availableMinor(), "creator credited 70% before refund");

    ledger.postRefundReversal("intent-1", "refund-1", Instant.now());

    // The creator credit is clawed back to zero (700 CREDIT + 700 DEBIT).
    assertEquals(0, ledger.balanceOf(creator).availableMinor(), "credit clawed back to zero");
    // The reversal rows are balanced (INV-6): one CREDIT provider, one DEBIT creator, one DEBIT fee.
    long signed =
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("refund-1"))
            .mapToLong(LedgerEntry::signedMinor)
            .sum();
    assertEquals(0, signed, "refund reversal is balanced (Sum debits = Sum credits)");
    assertEquals(
        3,
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("refund-1"))
            .count(),
        "three mirror rows (provider credit, creator debit, fee debit)");
  }

  @Test
  void clawback_after_the_creator_already_withdrew_drives_available_negative() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("creator-2");
    postSale(ledger, "intent-2", creator); // +700 available
    // Creator withdraws the whole ₵7.00 (reserve debits creator_payable -> available 0).
    ledger.postWithdrawalReserve(creator, ghs(700), "wd-1", Instant.now());
    assertEquals(0, ledger.balanceOf(creator).availableMinor(), "available drained by the withdrawal");

    // Refund now claws back the 700 credit the creator no longer holds → NEGATIVE (recovery owed).
    ledger.postRefundReversal("intent-2", "refund-2", Instant.now());
    assertEquals(
        -700, ledger.balanceOf(creator).availableMinor(),
        "clawback of an already-withdrawn credit drives available negative (owed), not skipped");
  }

  @Test
  void a_duplicate_refund_reversal_is_rejected_exactly_once() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("creator-3");
    postSale(ledger, "intent-3", creator);

    ledger.postRefundReversal("intent-3", "refund-3", Instant.now());
    // A second reversal for the SAME refund id fails on the exactly-once claim (ledger_posting PK).
    assertThrows(
        DuplicatePostingException.class,
        () -> ledger.postRefundReversal("intent-3", "refund-3", Instant.now()),
        "a duplicate refund can never double-clawback");
    // Balance stayed at zero (exactly one clawback applied).
    assertEquals(0, ledger.balanceOf(creator).availableMinor());
  }

  @Test
  void reversing_an_intent_with_no_settlement_entries_is_an_error() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    assertThrows(
        IllegalStateException.class,
        () -> ledger.postRefundReversal("nonexistent-intent", "refund-x", Instant.now()));
  }

  @Test
  void multi_creator_order_reverses_every_per_creator_sub_posting() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId a = new AccountId("creator-a");
    AccountId b = new AccountId("creator-b");
    // A ≥2-creator order posts per-creator sub-postings keyed "<intentId>:<creatorId>".
    postPerCreator(ledger, "intent-9", "intent-9:creator-a", a, 600);
    postPerCreator(ledger, "intent-9", "intent-9:creator-b", b, 400);
    assertEquals(420, ledger.balanceOf(a).availableMinor()); // 70% of 600
    assertEquals(280, ledger.balanceOf(b).availableMinor()); // 70% of 400

    ledger.postRefundReversal("intent-9", "refund-9", Instant.now());
    assertEquals(0, ledger.balanceOf(a).availableMinor(), "creator A clawed back");
    assertEquals(0, ledger.balanceOf(b).availableMinor(), "creator B clawed back");
  }

  private static void postPerCreator(
      FakeLedgerRepository ledger, String intentId, String refId, AccountId creator, long gross) {
    long share = Math.round(gross * 0.70);
    long fee = gross - share;
    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn"));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId platformRevenue =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null));
    TxnId txn = new TxnId("sale-" + refId);
    Instant now = Instant.now();
    ledger.claimPosting(txn, "intent", refId);
    ledger.postBalanced(
        txn,
        List.of(
            LedgerEntry.post("p1-" + refId, txn, providerClearing, Direction.DEBIT, ghs(gross), "intent", refId, now, now),
            LedgerEntry.post("p2-" + refId, txn, creatorPayable, Direction.CREDIT, ghs(share), "intent", refId, now, now),
            LedgerEntry.post("p3-" + refId, txn, platformRevenue, Direction.CREDIT, ghs(fee), "intent", refId, now, now)));
    assertTrue(true);
  }
}
