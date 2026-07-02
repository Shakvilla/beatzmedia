package org.shakvilla.beatzmedia.payments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.shakvilla.beatzmedia.payments.application.service.LedgerPostingService;
import org.shakvilla.beatzmedia.payments.domain.AccountId;
import org.shakvilla.beatzmedia.payments.domain.Direction;
import org.shakvilla.beatzmedia.payments.domain.LedgerEntry;
import org.shakvilla.beatzmedia.payments.domain.Provider;
import org.shakvilla.beatzmedia.payments.domain.RevenueSplit;
import org.shakvilla.beatzmedia.payments.fakes.FakeLedgerRepository;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link LedgerPostingService}: the sale (70/30) and tip (90/10) split postings are
 * balanced (INV-6), post the exact three rows the ADD §3 examples prescribe, and read the fee
 * percentage from the caller (never hard-coded — INV-4/INV-11). Uses {@link FakeLedgerRepository}
 * which itself asserts balance.
 */
@Tag("unit")
class LedgerPostingServiceTest {

  private static final Instant T = Instant.parse("2026-07-02T00:00:00Z");
  private FakeLedgerRepository ledger;
  private LedgerPostingService service;

  @BeforeEach
  void setUp() {
    ledger = new FakeLedgerRepository();
    service = new LedgerPostingService(ledger, FakeIds.sequential("id"));
  }

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  @Test
  void sale_split_posts_debit_clearing_credit_creator_credit_platform_balanced() {
    RevenueSplit split =
        service.postSaleSplit(
            Provider.mtn, new AccountId("creator-1"), ghs(1000), 30, "intent-1", T);

    assertEquals(700, split.creatorShare().minor());
    assertEquals(300, split.platformFee().minor());

    // Exactly three rows, all sharing one txn, balanced (Σdebits == Σcredits).
    assertEquals(3, ledger.entries.size());
    long debit = signed(Direction.DEBIT);
    long credit = -signed(Direction.CREDIT);
    assertEquals(debit, credit, "Σdebits must equal Σcredits (INV-6)");
    assertEquals(1000, debit);

    // One debit of 1000 (provider clearing) and two credits (700 + 300).
    assertEquals(1000, amountFor(Direction.DEBIT));
    assertEquals(1000, amountFor(Direction.CREDIT));

    // Creator balance reflects the 700 credit.
    assertEquals(700, ledger.balanceOf(new AccountId("creator-1")).availableMinor());
  }

  @Test
  void tip_split_posts_90_10_from_supplied_pct_balanced() {
    RevenueSplit split =
        service.postTipSplit(
            Provider.mtn, new AccountId("creator-2"), ghs(1000), 10, "intent-2", T);

    assertEquals(900, split.creatorShare().minor());
    assertEquals(100, split.platformFee().minor());
    assertEquals(3, ledger.entries.size());
    assertEquals(1000, amountFor(Direction.DEBIT));
    assertEquals(1000, amountFor(Direction.CREDIT));
    assertEquals(900, ledger.balanceOf(new AccountId("creator-2")).availableMinor());

    // All rows carry the tip ref_type so the admin ledger classifies them as tips.
    assertTrue(ledger.entries.stream().allMatch(e -> e.getRefType().equals("tip")));
  }

  @Test
  void odd_amount_tip_reconciles_remainder_to_creator_and_stays_balanced() {
    service.postTipSplit(Provider.card, new AccountId("creator-3"), ghs(333), 10, "intent-3", T);
    assertEquals(300, ledger.balanceOf(new AccountId("creator-3")).availableMinor());
    assertEquals(333, amountFor(Direction.DEBIT));
    assertEquals(333, amountFor(Direction.CREDIT));
  }

  @Test
  void zero_fee_pct_omits_platform_row_but_still_balances() {
    // Edge: 100% to creator, 0 fee → the platform-revenue row is omitted (0 amount), still balanced.
    service.postSaleSplit(Provider.bank, new AccountId("creator-4"), ghs(500), 0, "intent-4", T);
    assertEquals(2, ledger.entries.size()); // debit clearing + credit creator only
    assertEquals(500, ledger.balanceOf(new AccountId("creator-4")).availableMinor());
    assertEquals(500, amountFor(Direction.DEBIT));
    assertEquals(500, amountFor(Direction.CREDIT));
  }

  private long signed(Direction dir) {
    return ledger.entries.stream()
        .filter(e -> e.getDirection() == dir)
        .mapToLong(LedgerEntry::signedMinor)
        .sum();
  }

  private long amountFor(Direction dir) {
    return ledger.entries.stream()
        .filter(e -> e.getDirection() == dir)
        .mapToLong(e -> e.getAmount().minor())
        .sum();
  }
}
