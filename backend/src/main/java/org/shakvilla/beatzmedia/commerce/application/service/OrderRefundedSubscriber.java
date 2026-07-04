package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import org.shakvilla.beatzmedia.commerce.application.port.in.RevokeOwnership;
import org.shakvilla.beatzmedia.payments.domain.OrderRefunded;

/**
 * Commerce's reaction to a payments refund (Commerce ADD §8, INV-9). The sanctioned cross-module link:
 * commerce <strong>consumes</strong> the {@code OrderRefunded} domain event — it never reads payments'
 * tables. The event carries the {@code orderRef}, which is all commerce needs to resolve its own order
 * and revoke the grants.
 *
 * <p><strong>INV-9.</strong> Fires {@code AFTER_SUCCESS} of the refund transaction — no ownership is
 * revoked before the ledger clawback is durably committed. The revoke runs in {@link
 * RevokeOwnershipService#revokeForRefundedOrder} ({@code REQUIRES_NEW}), whose order-row lock +
 * per-grant idempotent revoke make a re-delivered refund/chargeback event a single revocation. A
 * refund for a reference commerce does not own (e.g. a tip refund) resolves to no order and no-ops.
 */
@ApplicationScoped
public class OrderRefundedSubscriber {

  private final RevokeOwnership revokeOwnership;

  @Inject
  public OrderRefundedSubscriber(RevokeOwnership revokeOwnership) {
    this.revokeOwnership = revokeOwnership;
  }

  /** On a completed refund: revoke ownership for the referenced order (INV-9). */
  public void onRefunded(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) OrderRefunded refunded) {
    revokeOwnership.revokeForRefundedOrder(refunded.orderRef());
  }
}
