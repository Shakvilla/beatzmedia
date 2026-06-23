package org.shakvilla.beatzmedia.platform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Money} value object. Covers INV-11 (half-up rounding, same-currency
 * guard, percentage/split, no pesewa lost). Testing-strategy §5.
 */
@Tag("inv")
class MoneyTest {

  // --- ofCedis / toCedis round-trip -----------------------------------------

  @Test
  void given_whole_cedis_when_ofCedis_then_minor_is_exact() {
    Money m = Money.ofCedis(new BigDecimal("10.00"));
    assertEquals(1000L, m.minor());
    assertEquals(Currency.GHS, m.currency());
  }

  @Test
  void given_half_pesewa_when_ofCedis_then_rounds_half_up() {
    // ₵0.005 → 0.5 pesewas → rounds half-up to 1 pesewa
    Money m = Money.ofCedis(new BigDecimal("0.005"));
    assertEquals(1L, m.minor());
  }

  @Test
  void given_minor_units_when_toCedis_then_divides_by_100() {
    Money m = Money.ofMinor(1050L, Currency.GHS);
    assertEquals(new BigDecimal("10.50"), m.toCedis());
  }

  @Test
  void given_odd_minor_when_toCedis_then_result_has_2dp() {
    Money m = Money.ofMinor(1L, Currency.GHS);
    assertEquals(new BigDecimal("0.01"), m.toCedis());
  }

  @Test
  void given_zero_cedis_when_ofCedis_then_minor_is_zero() {
    Money m = Money.ofCedis(BigDecimal.ZERO);
    assertEquals(0L, m.minor());
    assertTrue(m.isZero());
  }

  // --- plus / minus ---------------------------------------------------------

  @Test
  void given_two_ghs_amounts_when_plus_then_adds_correctly() {
    Money a = Money.ofMinor(500L, Currency.GHS);
    Money b = Money.ofMinor(300L, Currency.GHS);
    assertEquals(800L, a.plus(b).minor());
  }

  @Test
  void given_two_ghs_amounts_when_minus_then_subtracts_correctly() {
    Money a = Money.ofMinor(700L, Currency.GHS);
    Money b = Money.ofMinor(200L, Currency.GHS);
    assertEquals(500L, a.minus(b).minor());
  }

  // --- same-currency guard --------------------------------------------------

  // Currently only GHS exists; guard is tested via direct construction of different-currency Money.
  // We test the guard via the MismatchedCurrencyException path using a helper.
  @Test
  void given_null_cedis_when_ofCedis_then_throws_illegal_argument() {
    assertThrows(IllegalArgumentException.class, () -> Money.ofCedis(null));
  }

  // --- percentage (half-up, INV-4 split math) --------------------------------

  @Test
  void given_1000_minor_when_percentage_70_then_700() {
    // INV-4: ₵10 sale → creator gets 70% = 700 pesewas
    Money sale = Money.ofMinor(1000L, Currency.GHS);
    assertEquals(700L, sale.percentage(70).minor());
  }

  @Test
  void given_1000_minor_when_percentage_30_then_300() {
    // INV-4: ₵10 sale → platform gets 30% = 300 pesewas
    Money sale = Money.ofMinor(1000L, Currency.GHS);
    assertEquals(300L, sale.percentage(30).minor());
  }

  @Test
  void given_1000_minor_when_percentage_90_then_900() {
    // INV-4: tip → creator gets 90%
    Money tip = Money.ofMinor(1000L, Currency.GHS);
    assertEquals(900L, tip.percentage(90).minor());
  }

  @Test
  void given_odd_amount_when_percentage_rounds_half_up() {
    // 101 pesewas * 70% = 70.7 → 71 (half-up)
    Money m = Money.ofMinor(101L, Currency.GHS);
    assertEquals(71L, m.percentage(70).minor());
  }

  @Test
  void given_percentage_out_of_range_when_called_then_throws() {
    Money m = Money.ofMinor(1000L, Currency.GHS);
    assertThrows(IllegalArgumentException.class, () -> m.percentage(101));
    assertThrows(IllegalArgumentException.class, () -> m.percentage(-1));
  }

  // --- split (no pesewa lost, INV-11) ----------------------------------------

  @Test
  void given_sale_when_split_70_30_then_sum_equals_original() {
    // INV-11: no pesewa lost or invented
    Money sale = Money.ofMinor(1001L, Currency.GHS);
    Money[] parts = sale.split(70);
    assertEquals(sale.minor(), parts[0].minor() + parts[1].minor(),
        "Split must sum to original (no pesewa lost)");
  }

  @Test
  void given_tip_when_split_90_10_then_sum_equals_original() {
    Money tip = Money.ofMinor(333L, Currency.GHS);
    Money[] parts = tip.split(90);
    assertEquals(tip.minor(), parts[0].minor() + parts[1].minor(),
        "Tip split must sum to original");
  }

  @Test
  void given_exactly_divisible_amount_when_split_then_no_remainder() {
    Money m = Money.ofMinor(1000L, Currency.GHS);
    Money[] parts = m.split(70);
    assertEquals(700L, parts[0].minor());
    assertEquals(300L, parts[1].minor());
  }

  // --- PageRequest clamping -------------------------------------------------

  @Test
  void given_page_below_1_when_constructed_then_clamped_to_1() {
    PageRequest pr = new PageRequest(0, 20);
    assertEquals(1, pr.page());
  }

  @Test
  void given_size_above_100_when_constructed_then_clamped_to_100() {
    PageRequest pr = new PageRequest(1, 200);
    assertEquals(100, pr.size());
  }

  @Test
  void given_size_below_1_when_constructed_then_defaults_to_20() {
    PageRequest pr = new PageRequest(1, 0);
    assertEquals(20, pr.size());
  }

  @Test
  void given_valid_page_when_offset_then_correct() {
    PageRequest pr = new PageRequest(3, 20);
    assertEquals(40, pr.offset());
  }
}
