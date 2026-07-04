package org.shakvilla.beatzmedia.commerce.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.commerce.application.port.in.RevokeOwnership;
import org.shakvilla.beatzmedia.commerce.application.port.out.OrderRepository;
import org.shakvilla.beatzmedia.commerce.application.port.out.OwnershipRepository;
import org.shakvilla.beatzmedia.commerce.domain.IllegalOrderTransitionException;
import org.shakvilla.beatzmedia.commerce.domain.Order;
import org.shakvilla.beatzmedia.commerce.domain.OwnershipGrant;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for {@link RevokeOwnership} (INV-9) — the ONLY code path that revokes an {@link
 * OwnershipGrant}. Invoked solely by {@link OrderRefundedSubscriber} on a completed payments refund
 * (Commerce ADD §8 / payments {@code OrderRefunded}). Runs in its OWN transaction
 * ({@code REQUIRES_NEW}) so it is isolated from the settlement/refund transaction that emitted the
 * event, mirroring the {@link GrantOwnershipService} pattern.
 *
 * <p>Steps (all atomic in one transaction):
 *
 * <ol>
 *   <li>Resolve the order by its reference (locked FOR UPDATE) — no cross-module read; the reference
 *       travels on the {@code OrderRefunded} event.
 *   <li>Guard {@code paid → refunded} (INV-9). An already-{@code refunded} order is a benign no-op
 *       (re-delivered refund event); a non-paid order is ignored (defense-in-depth).
 *   <li>Revoke EVERY active grant for the order (INV-9). Album/season purchases materialised one
 *       grant per constituent track/episode at grant time (INV-2), so this revokes them all. Revoke
 *       is idempotent per grant (re-revoking is a no-op), so a re-delivered event never double-acts.
 *   <li>Audit the revocation (INV-10).
 * </ol>
 *
 * <p><strong>Idempotency / concurrency.</strong> The order-row FOR UPDATE lock serialises two
 * concurrent refund events for the same order; the second observes {@code refunded} and no-ops. Each
 * grant's {@code revoke} is idempotent. So a re-delivered refund/chargeback event, or two concurrent
 * refunds, never double-revoke ownership.
 */
@ApplicationScoped
public class RevokeOwnershipService implements RevokeOwnership {

  private static final Logger LOG = Logger.getLogger(RevokeOwnershipService.class);

  private final OrderRepository orderRepository;
  private final OwnershipRepository ownershipRepository;
  private final AuditWriter auditWriter;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public RevokeOwnershipService(
      OrderRepository orderRepository,
      OwnershipRepository ownershipRepository,
      AuditWriter auditWriter,
      IdGenerator ids,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.ownershipRepository = ownershipRepository;
    this.auditWriter = auditWriter;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public void revokeForRefundedOrder(String orderReference) {
    Order order = orderRepository.findByReferenceForUpdate(orderReference).orElse(null);
    if (order == null) {
      // Unknown order ref (e.g. a tip refund, or a foreign reference) — nothing to revoke.
      LOG.debugf("no commerce order for refunded reference %s; skipping revoke", orderReference);
      return;
    }

    // INV-9: transition paid -> refunded. An already-refunded order is a benign no-op (re-delivery).
    boolean transitioned;
    try {
      transitioned = order.markRefunded();
    } catch (IllegalOrderTransitionException e) {
      LOG.warnf(
          "refund for order %s in status %s cannot revoke; ignoring",
          order.getReference(), order.getStatus().wireValue());
      return;
    }
    if (!transitioned) {
      LOG.debugf("order %s already refunded; skipping duplicate revoke", order.getReference());
      return;
    }

    orderRepository.save(order); // persist refunded status

    // Revoke every grant for the order (INV-9). Idempotent per grant; album/season → all units.
    List<OwnershipGrant> grants = ownershipRepository.findBySourceOrder(order.getId());
    int revoked = 0;
    for (OwnershipGrant grant : grants) {
      if (grant.isActive()) {
        grant.revoke(clock.now());
        ownershipRepository.update(grant);
        revoked++;
      }
    }

    auditWriter.append(
        new AuditEntry(
            ids.newId(),
            order.getAccountId().value(),
            "OWNERSHIP_REVOKED",
            "Order",
            order.getId().value(),
            AuditType.FINANCE,
            "grantsRevoked=" + revoked,
            clock.now()));

    LOG.debugf("revoked %d grant(s) for refunded order %s (INV-9)", revoked, order.getReference());
  }
}
