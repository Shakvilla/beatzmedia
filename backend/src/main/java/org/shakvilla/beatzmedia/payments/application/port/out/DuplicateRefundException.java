package org.shakvilla.beatzmedia.payments.application.port.out;

/**
 * Thrown by {@link DisputeRepository#saveRefund} when a refund already exists for the dispute
 * ({@code uq_refund_per_dispute}, V705). The durable exactly-once guard that makes a retried /
 * concurrent refund of the same dispute unable to double-clawback (INV-9). An application-layer
 * technical exception (not a {@code DomainException}) — the caller catches it and treats the refund
 * as already-completed (a benign no-op), mirroring {@code DuplicatePayoutException} (WU-PAY-4).
 */
public class DuplicateRefundException extends RuntimeException {

  public DuplicateRefundException(String disputeId, Throwable cause) {
    super("a refund already exists for dispute " + disputeId, cause);
  }
}
