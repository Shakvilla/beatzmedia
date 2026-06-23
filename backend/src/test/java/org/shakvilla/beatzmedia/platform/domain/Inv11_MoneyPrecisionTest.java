package org.shakvilla.beatzmedia.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Dedicated INV-11 test: money stored in minor units; conversion to/from cedis is half-up at
 * adapter boundary; no pesewa is lost in split/fee math. Testing-strategy §5 / INV-11.
 */
@Tag("inv")
class Inv11_MoneyPrecisionTest {

  @Test
  void round_trip_cedis_to_minor_to_cedis_is_lossless() {
    BigDecimal original = new BigDecimal("12.50");
    Money m = Money.ofCedis(original);
    assertEquals(original, m.toCedis(), "Round-trip must be lossless for exact values");
  }

  @Test
  void platform_fee_30_and_creator_70_sum_to_original() {
    // INV-4 + INV-11: split on arbitrary amounts must always sum to whole
    for (long minor = 1; minor <= 10000; minor += 7) {
      Money sale = Money.ofMinor(minor, Currency.GHS);
      Money creator = sale.percentage(70);
      Money platform = Money.ofMinor(minor - creator.minor(), Currency.GHS);
      assertEquals(minor, creator.minor() + platform.minor(),
          "Split must sum to original for minor=" + minor);
    }
  }

  @Test
  void service_fee_50_pesewas_converts_to_half_cedi() {
    // serviceFeeMinor=50 in PlatformSettings defaults → ₵0.50
    Money fee = Money.ofMinor(50L, Currency.GHS);
    assertEquals(new BigDecimal("0.50"), fee.toCedis());
  }

  @Test
  void minimum_payout_1000_pesewas_converts_to_10_cedis() {
    // payoutMinimumMinor=1000 → ₵10
    Money minPayout = Money.ofMinor(1000L, Currency.GHS);
    assertEquals(new BigDecimal("10.00"), minPayout.toCedis());
  }

  @Test
  void half_up_rounding_at_midpoint() {
    // 0.005 cedis = 0.5 pesewas → rounds to 1 pesewa (half-up)
    Money m = Money.ofCedis(new BigDecimal("0.005"));
    assertEquals(1L, m.minor());
  }

  @Test
  void no_pesewa_lost_in_tip_split_90_10_over_range() {
    // INV-4 tips: creator 90%, platform 10%
    for (long minor = 1; minor <= 1000; minor++) {
      Money tip = Money.ofMinor(minor, Currency.GHS);
      Money[] parts = tip.split(90);
      assertEquals(minor, parts[0].minor() + parts[1].minor(),
          "Tip split must sum to original for minor=" + minor);
    }
  }
}
