package org.shakvilla.beatzmedia.commerce.domain;

/**
 * Order lifecycle status. Lifted verbatim from the frontend / PRD §6.5. Commerce ADD §3.
 *
 * <pre>
 *   pending --> paid       (PaymentSettled — grant ownership, INV-1)
 *   pending --> failed     (PaymentFailed — cart preserved, no grant)
 *   paid    --> fulfilled  (tickets issued / digital delivered)
 *   paid    --> refunded   (refund completed — RevokeOwnership, INV-9)
 * </pre>
 */
public enum OrderStatus {
  pending,
  paid,
  fulfilled,
  refunded,
  failed;

  /** Wire value equals the enum constant name exactly (lowercase). */
  public String wireValue() {
    return name();
  }

  /** Terminal states never transition to a grant/charge again. */
  public boolean isTerminal() {
    return this == refunded || this == failed;
  }
}
