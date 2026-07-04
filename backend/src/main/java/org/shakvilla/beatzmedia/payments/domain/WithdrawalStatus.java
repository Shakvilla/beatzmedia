package org.shakvilla.beatzmedia.payments.domain;

/**
 * Lifecycle of a {@link WithdrawalRequest} (payments ADD §3). Mirrors the {@code
 * withdrawal_request.status} CHECK (V704).
 *
 * <ul>
 *   <li>{@link #PENDING} — reserved (funds already debited from available on request), awaiting a
 *       payout run.
 *   <li>{@link #READY} — a KYC-verified reserved request eligible for a run (synonym of pending
 *       here; the admin pending-payouts list surfaces both as payable).
 *   <li>{@link #PAID} — executed by a payout run; a {@link PayoutTxn} exists.
 *   <li>{@link #FAILED} — the reservation was reversed.
 * </ul>
 */
public enum WithdrawalStatus {
  PENDING,
  READY,
  PAID,
  FAILED;

  /** Wire/DB token (lower-case), e.g. {@code pending}. */
  public String wire() {
    return name().toLowerCase();
  }

  /** Parse a wire/DB token to the enum. */
  public static WithdrawalStatus fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("withdrawal status must not be null");
    }
    return valueOf(value.trim().toUpperCase());
  }

  /** True iff a payout run may execute a withdrawal in this state (not already paid/failed). */
  public boolean isPayable() {
    return this == PENDING || this == READY;
  }
}
