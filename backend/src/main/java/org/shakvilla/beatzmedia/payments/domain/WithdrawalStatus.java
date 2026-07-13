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
 *   <li>{@link #SENT} — the cashout was sent to an async rail (Redde) and is in flight (WU-PAY-7);
 *       confirmed settlement arrives via the cashout webhook or the payout recon poll. The
 *       synchronous sandbox path (flag off) skips this state and lands straight on {@link #PAID}.
 *   <li>{@link #PAID} — confirmed settled; a {@link PayoutTxn} exists and the disbursement is posted.
 *   <li>{@link #FAILED} — the cashout failed (reservation reversal is a documented non-goal, ADR-28).
 * </ul>
 */
public enum WithdrawalStatus {
  PENDING,
  READY,
  SENT,
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

  /** True iff a payout run may execute a withdrawal in this state (reserved, not yet sent/paid). */
  public boolean isPayable() {
    return this == PENDING || this == READY;
  }

  /** True iff a cashout is in flight on an async rail (awaiting webhook/recon confirmation). */
  public boolean isSent() {
    return this == SENT;
  }

  /** True iff this is a terminal state — no further disbursement transition is allowed. */
  public boolean isTerminal() {
    return this == PAID || this == FAILED;
  }
}
