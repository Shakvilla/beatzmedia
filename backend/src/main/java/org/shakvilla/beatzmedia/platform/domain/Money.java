package org.shakvilla.beatzmedia.platform.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable money value object storing amounts in integer minor units (pesewas). INV-11: all
 * arithmetic is half-up; conversion to/from cedis happens only at adapter boundaries.
 */
public record Money(long minor, Currency currency) {

  /** Factory: convert from decimal cedis to pesewas, rounding half-up. INV-11. */
  public static Money ofCedis(BigDecimal cedis, Currency currency) {
    if (cedis == null) {
      throw new IllegalArgumentException("cedis must not be null");
    }
    long pesewas = cedis.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    return new Money(pesewas, currency);
  }

  /** Factory: cedis shorthand for GHS (the platform default currency). */
  public static Money ofCedis(BigDecimal cedis) {
    return ofCedis(cedis, Currency.GHS);
  }

  /** Factory: construct directly from minor units. */
  public static Money ofMinor(long minor, Currency currency) {
    return new Money(minor, currency);
  }

  /** Convert to decimal cedis with 2 decimal places, rounding half-up. INV-11. */
  public BigDecimal toCedis() {
    return BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /**
   * Add two Money values. Throws {@link MismatchedCurrencyException} if currencies differ.
   * INV-11.
   */
  public Money plus(Money other) {
    guardSameCurrency(other);
    return new Money(this.minor + other.minor, this.currency);
  }

  /**
   * Subtract another Money from this. Throws {@link MismatchedCurrencyException} if currencies
   * differ. INV-11.
   */
  public Money minus(Money other) {
    guardSameCurrency(other);
    return new Money(this.minor - other.minor, this.currency);
  }

  /**
   * Calculate a percentage of this value. Rounding is half-up. INV-11.
   *
   * @param pct integer percentage (0–100)
   */
  public Money percentage(int pct) {
    if (pct < 0 || pct > 100) {
      throw new IllegalArgumentException("pct must be 0–100, got: " + pct);
    }
    long result = BigDecimal.valueOf(minor)
        .multiply(BigDecimal.valueOf(pct))
        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
        .longValueExact();
    return new Money(result, currency);
  }

  /**
   * Split this amount into two parts: the percentage portion and the remainder (ensuring the whole
   * is preserved). INV-11: no pesewa is lost or invented.
   *
   * @param pct percentage for the first part
   * @return array where [0] is the pct portion and [1] is the remainder
   */
  public Money[] split(int pct) {
    Money part = percentage(pct);
    Money remainder = new Money(this.minor - part.minor, this.currency);
    return new Money[]{part, remainder};
  }

  /** Returns true if this amount is zero. */
  public boolean isZero() {
    return minor == 0;
  }

  /** Returns true if this amount is positive. */
  public boolean isPositive() {
    return minor > 0;
  }

  private void guardSameCurrency(Money other) {
    if (this.currency != other.currency) {
      throw new MismatchedCurrencyException(
          "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
    }
  }

  @Override
  public String toString() {
    return toCedis().toPlainString() + " " + currency;
  }
}
