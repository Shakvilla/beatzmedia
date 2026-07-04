package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * A completed refund of a disputed order (payments ADD §3, INV-9). Persisted once per adjudicated
 * refund; its existence records that the ledger clawback reversal was posted and {@code OrderRefunded}
 * was emitted for the dispute. Immutable value object; framework-free. Money is minor units (INV-11).
 */
public record Refund(
    String id, DisputeId disputeId, String paymentIntentId, Money amount, String reason, Instant at) {

  public Refund {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(disputeId, "disputeId");
    if (paymentIntentId == null || paymentIntentId.isBlank()) {
      throw new IllegalArgumentException("paymentIntentId must not be blank");
    }
    Objects.requireNonNull(amount, "amount");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("refund amount must be positive");
    }
    Objects.requireNonNull(at, "at");
  }
}
