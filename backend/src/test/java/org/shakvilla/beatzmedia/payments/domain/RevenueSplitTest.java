package org.shakvilla.beatzmedia.payments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Unit tests for {@link RevenueSplit} — the load-bearing split math (INV-4/INV-6/INV-11). The
 * documented rounding rule is: platform fee = half-up percentage of gross; creator share = the exact
 * remainder, so creator + platform ALWAYS equals gross (no pesewa lost or invented). A verification
 * sub-agent adversarially re-derives these, so the worked examples from the payments ADD §3 are
 * asserted verbatim plus odd amounts and exhaustive remainder reconciliation.
 */
@Tag("unit")
class RevenueSplitTest {

  private static Money ghs(long minor) {
    return Money.ofMinor(minor, Currency.GHS);
  }

  // ---- ADD §3 worked examples (verbatim) --------------------------------

  @Test
  void sale_10_cedis_splits_700_creator_300_platform() {
    // ADD §3 sale example: ₵10.00 = 1000 @ 30% fee → creator 700, platform 300.
    RevenueSplit split = RevenueSplit.ofFeePct(ghs(1000), 30);
    assertEquals(700, split.creatorShare().minor());
    assertEquals(300, split.platformFee().minor());
    assertEquals(1000, split.gross().minor());
  }

  @Test
  void tip_10_cedis_splits_900_creator_100_platform() {
    // ADD §3 tip example: ₵10.00 = 1000 @ 10% fee → creator 900, platform 100.
    RevenueSplit split = RevenueSplit.ofFeePct(ghs(1000), 10);
    assertEquals(900, split.creatorShare().minor());
    assertEquals(100, split.platformFee().minor());
  }

  // ---- odd amounts (rounding + remainder reconciliation) ----------------

  @Test
  void sale_999_pesewas_30pct_fee_rounds_half_up_and_reconciles() {
    // ₵9.99 = 999 @ 30% fee → fee round(299.7) = 300, creator = 999 − 300 = 699.
    RevenueSplit split = RevenueSplit.ofFeePct(ghs(999), 30);
    assertEquals(300, split.platformFee().minor());
    assertEquals(699, split.creatorShare().minor());
    assertEquals(999, split.creatorShare().minor() + split.platformFee().minor());
  }

  @Test
  void tip_333_pesewas_10pct_fee_rounds_and_reconciles() {
    // 333 @ 10% fee → fee round(33.3) = 33, creator = 300.
    RevenueSplit split = RevenueSplit.ofFeePct(ghs(333), 10);
    assertEquals(33, split.platformFee().minor());
    assertEquals(300, split.creatorShare().minor());
    assertEquals(333, split.creatorShare().minor() + split.platformFee().minor());
  }

  @ParameterizedTest
  @CsvSource({
    // gross, feePct, expectedFee, expectedCreator
    "1, 30, 0, 1", // 1 @ 30% → round(0.3)=0 fee, creator 1
    "2, 30, 1, 1", // 2 @ 30% → round(0.6)=1 fee, creator 1
    "5, 30, 2, 3", // 5 @ 30% → round(1.5)=2 (half-up), creator 3
    "15, 30, 5, 10", // 15 @ 30% → round(4.5)=5 (half-up on .5), creator 10
    "333, 10, 33, 300",
    "999, 30, 300, 699",
    "1000, 30, 300, 700",
    "1000, 10, 100, 900",
    "12345, 30, 3704, 8641", // round(3703.5)=3704 half-up
    "2500, 10, 250, 2250"
  })
  void split_preserves_gross_and_rounds_half_up(
      long gross, int feePct, long expectedFee, long expectedCreator) {
    RevenueSplit split = RevenueSplit.ofFeePct(ghs(gross), feePct);
    assertEquals(expectedFee, split.platformFee().minor(), "fee");
    assertEquals(expectedCreator, split.creatorShare().minor(), "creator");
    // No pesewa lost or invented (INV-6/INV-11).
    assertEquals(gross, split.creatorShare().minor() + split.platformFee().minor());
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 7, 13, 99, 100, 101, 250, 999, 1000, 123457, 7777777})
  void creator_plus_platform_always_equals_gross_for_any_amount(long gross) {
    for (int feePct = 0; feePct <= 100; feePct++) {
      RevenueSplit split = RevenueSplit.ofFeePct(ghs(gross), feePct);
      assertEquals(
          gross,
          split.creatorShare().minor() + split.platformFee().minor(),
          "gross=" + gross + " feePct=" + feePct);
      // Neither part is ever negative.
      org.junit.jupiter.api.Assertions.assertTrue(split.creatorShare().minor() >= 0);
      org.junit.jupiter.api.Assertions.assertTrue(split.platformFee().minor() >= 0);
    }
  }

  // ---- guards -----------------------------------------------------------

  @Test
  void rejects_non_positive_gross() {
    assertThrows(IllegalArgumentException.class, () -> RevenueSplit.ofFeePct(ghs(0), 30));
    assertThrows(IllegalArgumentException.class, () -> RevenueSplit.ofFeePct(ghs(-5), 30));
  }

  @Test
  void rejects_null_gross() {
    assertThrows(IllegalArgumentException.class, () -> RevenueSplit.ofFeePct(null, 30));
  }

  @Test
  void rejects_fee_pct_out_of_range() {
    assertThrows(IllegalArgumentException.class, () -> RevenueSplit.ofFeePct(ghs(1000), 101));
    assertThrows(IllegalArgumentException.class, () -> RevenueSplit.ofFeePct(ghs(1000), -1));
  }

  @Test
  void reconstruction_guard_rejects_parts_that_do_not_sum_to_gross() {
    assertThrows(
        IllegalStateException.class,
        () -> new RevenueSplit(ghs(700), ghs(299), ghs(1000))); // 700 + 299 != 1000
  }
}
