package org.shakvilla.beatzmedia.payments.application.port.in;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Wire representation of money: {@code { "amount": <decimal cedis>, "currency": "GHS" }}. Matches
 * the {@code Money} TypeScript type in {@code Frontend/src/types/index.ts} and INV-11 (minor units
 * stored; decimal cedis on the wire). Payments ADD §6.
 */
public record MoneyView(BigDecimal amount, String currency) {

  /** Convert from a domain {@link Money} value object to the wire representation. */
  public static MoneyView of(Money money) {
    return new MoneyView(money.toCedis(), money.currency().name());
  }

  /** Convert from pesewas (minor units), assuming GHS. */
  public static MoneyView ofMinor(long pesewas) {
    BigDecimal cedis =
        BigDecimal.valueOf(pesewas).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new MoneyView(cedis, "GHS");
  }
}
