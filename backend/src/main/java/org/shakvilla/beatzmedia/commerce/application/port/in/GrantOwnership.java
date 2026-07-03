package org.shakvilla.beatzmedia.commerce.application.port.in;

/**
 * Internal input port (Commerce ADD §4.1) — invoked <strong>only</strong> by the {@code
 * PaymentSettled} event handler (INV-1). No REST surface, no other caller: an ArchUnit/usage test
 * proves ownership is created solely on settlement.
 *
 * <p>Grants ownership for a settled order: transitions the order {@code pending → paid}, expands each
 * album/season line to its constituent track/episode grants (INV-2), creates one {@link
 * org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant} per unit, posts the 70/30 sale split
 * (INV-4), clears the cart, and publishes {@code OwnershipGranted}. Idempotent on the order: a
 * re-delivered settlement produces no duplicate grants, credits, or cart clears (exactly-once claim +
 * unique-active grant indexes).
 */
public interface GrantOwnership {

  /**
   * Grant ownership for the order referenced by a settled payment.
   *
   * @param orderReference the {@code BZ-YYYY-NNNNN} reference carried by {@code PaymentSettled}
   * @param paymentIntentId the settled intent id (traced onto the ledger split)
   * @param provider the settled rail (for the ledger debit account)
   */
  void grantForSettledOrder(String orderReference, String paymentIntentId, String provider);
}
