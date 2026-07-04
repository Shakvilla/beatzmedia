package org.shakvilla.beatzmedia.payments.domain;

/**
 * The kind of admin payout run (payments ADD §3). Mirrors the {@code payout_batch.kind} CHECK
 * (V704). A {@link #WEEKLY} run pays all payable withdrawals; a {@link #SINGLE} run pays one.
 */
public enum PayoutBatchKind {
  WEEKLY,
  SINGLE;

  /** Wire/DB token (lower-case), e.g. {@code weekly}. */
  public String wire() {
    return name().toLowerCase();
  }
}
