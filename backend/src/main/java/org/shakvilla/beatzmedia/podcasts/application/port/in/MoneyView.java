package org.shakvilla.beatzmedia.podcasts.application.port.in;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Wire representation of money: {@code { "amount": <decimal cedis>, "currency": "GHS" }}. Matches
 * the {@code Money} TypeScript type in {@code Frontend/src/types/index.ts} and INV-11 (minor units
 * stored; decimal cedis on the wire). ADD §6.
 */
public record MoneyView(BigDecimal amount, String currency) {

  /** Convert from pesewas (minor units) to the wire representation. */
  public static MoneyView ofMinor(long pesewas, String currency) {
    BigDecimal cedis =
        BigDecimal.valueOf(pesewas).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    return new MoneyView(cedis, currency);
  }
}
