package org.shakvilla.beatzmedia.payments.domain;

/**
 * Lifecycle of a {@link PayoutTxn} (WU-PAY-7). Mirrors the {@code payout_txn.status} CHECK (V967).
 *
 * <ul>
 *   <li>{@link #SENT} — the cashout was sent to the rail (async gateway); the ledger disbursement has
 *       NOT been posted yet and {@code disburse_txn_id} is null. Awaiting the cashout webhook / recon
 *       poll.
 *   <li>{@link #PAID} — confirmed settled: the balanced disbursement ledger txn was posted and traces
 *       via {@code disburse_txn_id}. The synchronous sandbox path (flag off) lands straight here.
 *   <li>{@link #FAILED} — the cashout failed; the reservation reversal is an explicit non-goal
 *       (ADR-28 addendum), so no ledger disbursement is posted.
 * </ul>
 */
public enum PayoutTxnStatus {
  SENT,
  PAID,
  FAILED;

  /** Wire/DB token (lower-case), e.g. {@code sent}. */
  public String wire() {
    return name().toLowerCase();
  }

  /** Parse a wire/DB token to the enum. */
  public static PayoutTxnStatus fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("payout txn status must not be null");
    }
    return valueOf(value.trim().toUpperCase());
  }
}
