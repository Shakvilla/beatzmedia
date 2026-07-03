package org.shakvilla.beatzmedia.commerce.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.commerce.application.port.in.GrantOwnership;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;

/**
 * Commerce's reaction to payments settlement events (Commerce ADD §2, §8). This is the sanctioned
 * cross-module link: commerce <strong>consumes</strong> the {@code PaymentSettled}/{@code
 * PaymentFailed} domain events — it never reads payments' tables or calls a payments read port. The
 * events carry the {@code orderRef} + minimal snapshot, which is all commerce needs to resolve its
 * own order.
 *
 * <p><strong>INV-1.</strong> Both observers fire {@code AFTER_SUCCESS} of the settlement transaction —
 * no ownership is created before the payment is durably {@code settled}. The grant itself runs in
 * {@link GrantOwnershipService#grantForSettledOrder} ({@code REQUIRES_NEW}), whose exactly-once claim
 * makes a re-delivered settlement (webhook replay + poll race) a single grant set. A settlement for a
 * reference commerce does not own (e.g. a tip's {@code TIP:<creator>} ref) resolves to no order and is
 * a benign no-op.
 */
@ApplicationScoped
public class PaymentSettledSubscriber {

  private static final Logger LOG = Logger.getLogger(PaymentSettledSubscriber.class);

  private final GrantOwnership grantOwnership;
  private final OrderRepository orderRepository;

  @Inject
  public PaymentSettledSubscriber(GrantOwnership grantOwnership, OrderRepository orderRepository) {
    this.grantOwnership = grantOwnership;
    this.orderRepository = orderRepository;
  }

  /** On a settled payment: grant ownership for the referenced order (INV-1/INV-2/INV-4). */
  public void onSettled(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) PaymentSettled settled) {
    grantOwnership.grantForSettledOrder(
        settled.orderRef(), settled.intentId(), settled.provider());
  }

  /**
   * On a failed/timed-out payment: mark the order {@code failed} with the reason and preserve the cart
   * for retry (LLFR-COMMERCE-02.3). No grant is ever created. Idempotent on the order.
   */
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void onFailed(@Observes(during = TransactionPhase.AFTER_SUCCESS) PaymentFailed failed) {
    Order order = orderRepository.findByReferenceForUpdate(failed.orderRef()).orElse(null);
    if (order == null) {
      return; // not a commerce order (e.g. a tip) — nothing to fail
    }
    if (order.markFailed(failed.reason())) {
      orderRepository.save(order);
      LOG.debugf("order %s marked failed (%s); cart preserved", order.getReference(), failed.reason());
    }
  }
}
