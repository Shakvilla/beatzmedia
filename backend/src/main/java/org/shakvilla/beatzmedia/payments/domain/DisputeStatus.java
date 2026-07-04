package org.shakvilla.beatzmedia.payments.domain;

/**
 * Lifecycle status of a {@link Dispute} (payments ADD §3 / §8 state machine). Wire values match the
 * frontend / PRD §3.2: {@code open | refunded | rejected | escalated}.
 *
 * <p>Transitions (guarded in {@link Dispute}):
 *
 * <ul>
 *   <li>{@code open → refunded} — a completed refund (revoke + clawback, INV-9).
 *   <li>{@code open → rejected} — admin rejects the dispute with a reason.
 *   <li>{@code open → escalated} — admin escalates for further review.
 *   <li>{@code escalated → open} — re-opened after review (so it can then be adjudicated).
 * </ul>
 *
 * A {@code refunded} or {@code rejected} dispute is terminal — no further money movement.
 */
public enum DisputeStatus {
  open,
  refunded,
  rejected,
  escalated;

  public static DisputeStatus fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("dispute status must not be null");
    }
    return switch (value.trim().toLowerCase()) {
      case "open" -> open;
      case "refunded" -> refunded;
      case "rejected" -> rejected;
      case "escalated" -> escalated;
      default -> throw new IllegalArgumentException("unknown dispute status: " + value);
    };
  }

  public String wire() {
    return name();
  }

  /** Whether an admin adjudication (refund/reject/escalate) may act on the dispute. */
  public boolean isAdjudicable() {
    return this == open;
  }

  public boolean isTerminal() {
    return this == refunded || this == rejected;
  }
}
