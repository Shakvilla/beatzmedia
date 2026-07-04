package org.shakvilla.beatzmedia.payments.application.port.in;

import org.shakvilla.beatzmedia.payments.domain.WithdrawalRequest;

/**
 * Read model returned by a withdrawal request (LLFR-PAYMENTS-03.2). Carries the reserved amount + the
 * server-computed fee ({@code arrival} label is derived from the method kind at the REST boundary).
 * Money is wire {@code { amount, currency }} (INV-11). No domain type leaks across the port.
 */
public record WithdrawalView(
    String id,
    String status,
    MoneyView amount,
    MoneyView fee,
    String methodId,
    String requestedAt) {

  public static WithdrawalView of(WithdrawalRequest w) {
    return new WithdrawalView(
        w.getId().value(),
        w.getStatus().wire(),
        MoneyView.of(w.getAmount()),
        MoneyView.of(w.getFee()),
        w.getMethodId().value(),
        w.getRequestedAt() != null ? w.getRequestedAt().toString() : null);
  }
}
