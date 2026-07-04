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

    ledger.postRefundReversal("intent-1", "refund-1", ghs(1000), Instant.now());

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
    ledger.postRefundReversal("intent-2", "refund-2", ghs(1000), Instant.now());
    assertEquals(
        -700, ledger.balanceOf(creator).availableMinor(),
        "clawback of an already-withdrawn credit drives available negative (owed), not skipped");
  }

  @Test
  void a_duplicate_refund_reversal_is_rejected_exactly_once() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("creator-3");
    postSale(ledger, "intent-3", creator);

    ledger.postRefundReversal("intent-3", "refund-3", ghs(1000), Instant.now());
    // A second reversal for the SAME refund id fails on the exactly-once claim (ledger_posting PK).
    assertThrows(
        DuplicatePostingException.class,
        () -> ledger.postRefundReversal("intent-3", "refund-3", ghs(1000), Instant.now()),
        "a duplicate refund can never double-clawback");
    // Balance stayed at zero (exactly one clawback applied).
    assertEquals(0, ledger.balanceOf(creator).availableMinor());
  }

  @Test
  void reversing_an_intent_with_no_settlement_entries_is_an_error() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    assertThrows(
        IllegalStateException.class,
        () -> ledger.postRefundReversal("nonexistent-intent", "refund-x", ghs(100), Instant.now()));
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

    ledger.postRefundReversal("intent-9", "refund-9", ghs(1000), Instant.now());
    assertEquals(0, ledger.balanceOf(a).availableMinor(), "creator A clawed back");
    assertEquals(0, ledger.balanceOf(b).availableMinor(), "creator B clawed back");
  }

  // ---- F1: proportional PARTIAL refund ---------------------------------------

  /** Post a ₵5.00 sale split (creator 350 / platform 150) for an intent. */
  private static void postSale500(FakeLedgerRepository ledger, String intentId, AccountId creator) {
    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn"));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    LedgerAccountId platformRevenue =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PLATFORM_REVENUE, null));
    TxnId txn = new TxnId("sale5-" + intentId);
    Instant now = Instant.now();
    ledger.claimPosting(txn, "intent", intentId);
    ledger.postBalanced(
        txn,
        List.of(
            LedgerEntry.post("f1", txn, providerClearing, Direction.DEBIT, ghs(500), "intent", intentId, now, now),
            LedgerEntry.post("f2", txn, creatorPayable, Direction.CREDIT, ghs(350), "intent", intentId, now, now),
            LedgerEntry.post("f3", txn, platformRevenue, Direction.CREDIT, ghs(150), "intent", intentId, now, now)));
  }

  private long refundEntrySum(FakeLedgerRepository ledger, String refundId, Direction dir) {
    return ledger.entries.stream()
        .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals(refundId))
        .filter(e -> e.getDirection() == dir)
        .mapToLong(e -> e.getAmount().minor())
        .sum();
  }

  @Test
  void partial_refund_reverses_proportionally_and_stays_balanced() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("f1-creator");
    postSale500(ledger, "f1-intent", creator); // 350 creator / 150 platform, gross 500
    assertEquals(350, ledger.balanceOf(creator).availableMinor());

    // Refund ₵2.00 = 200 of the ₵5.00 sale. Proportional: platform = round(200*150/500)=60,
    // creator = 200-60 = 140 (exact remainder). Buyer credited 200.
    ledger.postRefundReversal("f1-intent", "f1-refund", ghs(200), Instant.now());

    // Creator credit reduced by ONLY the proportional 140 (was 350 → 210), NOT the full 350.
    assertEquals(210, ledger.balanceOf(creator).availableMinor(), "creator clawed back proportionally (140)");
    // DEBIT legs (platform 60 + creator 140) sum to exactly the refunded 200 = the buyer CREDIT.
    assertEquals(200, refundEntrySum(ledger, "f1-refund", Direction.DEBIT), "DEBIT legs sum to refund");
    assertEquals(200, refundEntrySum(ledger, "f1-refund", Direction.CREDIT), "buyer credited the refund");
    // Balanced (INV-6).
    long signed =
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1-refund"))
            .mapToLong(LedgerEntry::signedMinor)
            .sum();
    assertEquals(0, signed, "partial reversal is balanced");
  }

  @Test
  void partial_refund_odd_amount_hits_rounding_boundary_and_balances() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("f1-odd-creator");
    postSale500(ledger, "f1-odd", creator); // 350 / 150, gross 500

    // Refund ₵3.33 = 333. platform = round(333*150/500)=round(99.9)=100; creator = 333-100 = 233.
    ledger.postRefundReversal("f1-odd", "f1-odd-refund", ghs(333), Instant.now());

    // Buyer credited exactly the odd refund; DEBIT legs (platform 100 + creator 233) sum to 333.
    assertEquals(333, refundEntrySum(ledger, "f1-odd-refund", Direction.CREDIT), "buyer credited 333");
    assertEquals(333, refundEntrySum(ledger, "f1-odd-refund", Direction.DEBIT), "DEBIT legs sum to 333");
    // Creator clawed back exactly the remainder 233 (was 350 → 117), not the rounded proportional.
    assertEquals(117, ledger.balanceOf(creator).availableMinor(), "creator clawed back exact remainder 233");
    long signed =
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1-odd-refund"))
            .mapToLong(LedgerEntry::signedMinor)
            .sum();
    assertEquals(0, signed, "odd partial reversal is balanced");
  }

  @Test
  void full_refund_still_reverses_exactly_the_original_legs() {
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId creator = new AccountId("f1-full-creator");
    postSale500(ledger, "f1-full", creator); // 350 / 150

    // A FULL refund (amount == gross 500) reverses exactly the original legs (regression-safe).
    ledger.postRefundReversal("f1-full", "f1-full-refund", ghs(500), Instant.now());
    assertEquals(0, ledger.balanceOf(creator).availableMinor(), "full refund claws back all 350");
    // DEBIT legs = platform 150 + creator 350 = 500 = buyer CREDIT (exact original legs).
    assertEquals(500, refundEntrySum(ledger, "f1-full-refund", Direction.DEBIT), "DEBIT legs sum to full 500");
    assertEquals(500, refundEntrySum(ledger, "f1-full-refund", Direction.CREDIT), "buyer credited full 500");
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

  // ---- F1-residual: multi-creator PARTIAL refund (running-remainder distribution) --------------

  /**
   * Post a per-creator sub-posting with an EXPLICIT creator-payable credit and NO platform fee, so the
   * split gross == the creator credit and the creator-distribution rounding is isolated. Keyed
   * "&lt;intentId&gt;:&lt;creatorId&gt;" like a real multi-creator order.
   */
  private static void postCreatorLeg(
      FakeLedgerRepository ledger, String intentId, AccountId creator, long creditMinor) {
    String refId = intentId + ":" + creator.value();
    LedgerAccountId providerClearing =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.PROVIDER_CLEARING, "mtn"));
    LedgerAccountId creatorPayable =
        ledger.idOf(ledger.accountFor(LedgerAccountKind.CREATOR_PAYABLE, creator.value()));
    TxnId txn = new TxnId("sale-" + refId);
    Instant now = Instant.now();
    ledger.claimPosting(txn, "intent", refId);
    ledger.postBalanced(
        txn,
        List.of(
            LedgerEntry.post("cl1-" + refId, txn, providerClearing, Direction.DEBIT, ghs(creditMinor), "intent", refId, now, now),
            LedgerEntry.post("cl2-" + refId, txn, creatorPayable, Direction.CREDIT, ghs(creditMinor), "intent", refId, now, now)));
  }

  @Test
  void multi_creator_partial_refund_five_equal_shares_balances_no_overshoot() {
    // The exact overshoot repro: 5 equal-share creators (1 each, total 5), a partial refund whose
    // creator-side total = 3. Naive independent HALF_UP rounding gives round(3·1/5)=1 for the first
    // FOUR creators → assigned=4 before the fifth → last portion 3−4 = −1 (dropped) → DEBITs sum 4 ≠ 3
    // → UnbalancedLedgerException. The running-remainder rule must instead post portions summing to 3.
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId[] cs = new AccountId[5];
    for (int i = 0; i < 5; i++) {
      cs[i] = new AccountId("f1r-eq-" + i);
      postCreatorLeg(ledger, "f1r-eq", cs[i], 1); // 1 credit each; gross 5, no platform fee
    }

    // Partial refund of 3 (of the gross 5). No platform fee, so creatorReversalTotal = 3.
    ledger.postRefundReversal("f1r-eq", "f1r-eq-refund", ghs(3), Instant.now());

    // Every creator DEBIT ≥ 0 and they sum to EXACTLY 3 = the buyer CREDIT (balanced, INV-6).
    assertEquals(3, refundEntrySum(ledger, "f1r-eq-refund", Direction.DEBIT), "creator DEBITs sum to 3");
    assertEquals(3, refundEntrySum(ledger, "f1r-eq-refund", Direction.CREDIT), "buyer credited 3");
    long signed =
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1r-eq-refund"))
            .mapToLong(LedgerEntry::signedMinor)
            .sum();
    assertEquals(0, signed, "5-equal-share partial reversal is balanced (no overshoot)");
    // No creator over-clawed: each portion is 0 or 1 (never negative, never > its 1 credit).
    ledger.entries.stream()
        .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1r-eq-refund"))
        .filter(e -> e.getDirection() == Direction.DEBIT)
        .forEach(e -> assertTrue(e.getAmount().minor() >= 0 && e.getAmount().minor() <= 1,
            "each creator DEBIT within [0,1]: " + e.getAmount().minor()));
  }

  @Test
  void multi_creator_partial_refund_odd_shares_and_odd_amount_balances() {
    // Adversarial odd shares (7 / 11 / 13, total 31) + an odd partial refund. No platform fee, so
    // creatorReversalTotal = the refund. The running-remainder split must sum EXACTLY to the refund
    // with every portion ≥ 0 and ≤ that creator's original share.
    FakeLedgerRepository ledger = new FakeLedgerRepository();
    AccountId a = new AccountId("f1r-odd-a");
    AccountId b = new AccountId("f1r-odd-b");
    AccountId c = new AccountId("f1r-odd-c");
    postCreatorLeg(ledger, "f1r-odd", a, 7);
    postCreatorLeg(ledger, "f1r-odd", b, 11);
    postCreatorLeg(ledger, "f1r-odd", c, 13); // gross 31

    ledger.postRefundReversal("f1r-odd", "f1r-odd-refund", ghs(17), Instant.now()); // odd partial

    assertEquals(17, refundEntrySum(ledger, "f1r-odd-refund", Direction.DEBIT), "creator DEBITs sum to 17");
    assertEquals(17, refundEntrySum(ledger, "f1r-odd-refund", Direction.CREDIT), "buyer credited 17");
    long signed =
        ledger.entries.stream()
            .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1r-odd-refund"))
            .mapToLong(LedgerEntry::signedMinor)
            .sum();
    assertEquals(0, signed, "odd-share odd-amount multi-creator partial reversal is balanced");
    // Each creator is clawed back no more than its original share (7/11/13) and never negative.
    java.util.Map<String, Long> byCreator = new java.util.HashMap<>();
    ledger.entries.stream()
        .filter(e -> e.getRefType().equals("refund") && e.getRefId().equals("f1r-odd-refund"))
        .filter(e -> e.getDirection() == Direction.DEBIT)
        .forEach(e -> byCreator.merge(e.getAccountId().value(), e.getAmount().minor(), Long::sum));
    assertTrue(byCreator.values().stream().allMatch(v -> v >= 0), "no negative creator clawback");
  }
}
