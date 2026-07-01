package org.shakvilla.beatzmedia.payments.domain;

/**
 * Lifecycle status of a {@link PaymentIntent}. Mirrors the frontend {@code PaymentIntentStatus}
 * (PRD §3.2 / payments ADD §3). Pure Java, no framework imports.
 *
 * <p>State machine (payments ADD §8):
 *
 * <pre>
 *   pending --> settled : webhook/poll confirms
 *   pending --> failed  : webhook/poll rejects
 *   pending --> timeout : maxWindow elapsed (poll)
 * </pre>
 *
 * <p>WU-PAY-1 owns the {@code pending} entry state and the transition guards; the terminal
 * transitions ({@code settled}/{@code failed}/{@code timeout}) are driven by webhooks / the
 * reconciliation poll in WU-PAY-2. The transition rules live here so both work units share one
 * source of truth (INV-1: no value granted before {@code settled}).
 */
public enum PaymentIntentStatus {
  pending,
  settled,
  failed,
  timeout;

  /** True if this status is terminal (no further transition is legal). */
  public boolean isTerminal() {
    return this != pending;
  }

  /**
   * Returns whether a transition from this status to {@code target} is legal. Only {@code pending}
   * may transition, and only to a terminal state.
   */
  public boolean canTransitionTo(PaymentIntentStatus target) {
    return this == pending && target.isTerminal();
  }
}
