package org.shakvilla.beatzmedia.commerce.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Order aggregate root. Holds the immutable price snapshot taken at checkout and the order-status
 * state machine (Commerce ADD §3). Domain-layer; no framework imports.
 *
 * <p><strong>Invariants enforced here:</strong>
 *
 * <ul>
 *   <li><strong>INV-1</strong> — the transition to a state where ownership may be granted is
 *       {@code pending → paid} ({@link #markPaid}); it is guarded so a {@code failed}/{@code
 *       refunded} order never becomes {@code paid}. {@link #canGrant()} is the single predicate the
 *       grant use case consults, so no grant is ever created off a non-paid order.
 *   <li><strong>INV-11</strong> — totals ({@code subtotal = Σ line totals}, {@code fee}, {@code
 *       total = subtotal + fee}) are computed from the snapshot lines in minor units; the checkout
 *       service re-prices authoritatively (never from the client) before constructing the order.
 * </ul>
 */
public final class Order {

  private final OrderId id;
  private final AccountId accountId;
  private final String reference;
  private OrderStatus status;
  private final Money subtotal;
  private final Money fee;
  private final Money total;
  private String paymentIntentId;
  private String failureReason;
  private String idempotencyKey;
  private final List<OrderLine> lines;
  private final Instant createdAt;

  public Order(
      OrderId id,
      AccountId accountId,
      String reference,
      OrderStatus status,
      Money subtotal,
      Money fee,
      Money total,
      String paymentIntentId,
      String failureReason,
      String idempotencyKey,
      List<OrderLine> lines,
      Instant createdAt) {
    this.id = id;
    this.accountId = accountId;
    this.reference = reference;
    this.status = status;
    this.subtotal = subtotal;
    this.fee = fee;
    this.total = total;
    this.paymentIntentId = paymentIntentId;
    this.failureReason = failureReason;
    this.idempotencyKey = idempotencyKey;
    this.lines = new ArrayList<>(lines);
    this.createdAt = createdAt;
  }

  /**
   * Create a fresh {@code pending} order from re-priced snapshot lines. {@code subtotal}, {@code fee}
   * and {@code total} are computed here from the (server-priced) lines — the caller must NEVER pass
   * client-supplied amounts (Commerce ADD §12.2 / G1).
   *
   * @param serviceFee the flat service fee for a non-empty order (from {@code PlatformSettings})
   */
  public static Order create(
      OrderId id,
      AccountId accountId,
      String reference,
      List<OrderLine> lines,
      Money serviceFee,
      Currency currency,
      Instant createdAt) {
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("cannot create an order with no lines");
    }
    long subtotalMinor = lines.stream().mapToLong(l -> l.lineTotal().minor()).sum();
    Money subtotal = Money.ofMinor(subtotalMinor, currency);
    Money fee = serviceFee;
    Money total = subtotal.plus(fee);
    return new Order(
        id, accountId, reference, OrderStatus.pending, subtotal, fee, total, null, null, null,
        lines, createdAt);
  }

  /** Record the payment intent id returned by {@code InitiateCharge} on the pending order. */
  public void attachPaymentIntent(String paymentIntentId) {
    this.paymentIntentId = paymentIntentId;
  }

  /** Record the checkout idempotency key on the pending order (INV-1 / §9.2 short-circuit). */
  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /**
   * Transition {@code pending → paid} (INV-1). Idempotent: a re-delivered settlement on an
   * already-{@code paid} order is a no-op (returns {@code false}). A {@code failed}/{@code refunded}
   * order can never become {@code paid} — throws {@link IllegalOrderTransitionException}.
   *
   * @return {@code true} if this call performed the transition; {@code false} if already paid
   */
  public boolean markPaid() {
    if (status == OrderStatus.paid || status == OrderStatus.fulfilled) {
      return false; // already settled — idempotent no-op (re-delivered PaymentSettled)
    }
    if (status != OrderStatus.pending) {
      throw new IllegalOrderTransitionException(status.wireValue(), OrderStatus.paid.wireValue());
    }
    this.status = OrderStatus.paid;
    return true;
  }

  /**
   * Transition {@code pending → failed} with a reason (cart preserved, no grant). Idempotent: a
   * re-delivered failure on an already-{@code failed} order is a no-op.
   *
   * @return {@code true} if this call performed the transition
   */
  public boolean markFailed(String reason) {
    if (status == OrderStatus.failed) {
      return false;
    }
    if (status != OrderStatus.pending) {
      throw new IllegalOrderTransitionException(status.wireValue(), OrderStatus.failed.wireValue());
    }
    this.status = OrderStatus.failed;
    this.failureReason = reason;
    return true;
  }

  /**
   * Transition {@code paid → refunded} (INV-9) — driven by a completed refund from payments. Grants
   * are revoked by the {@link OwnershipGrant} revoke path in the same handler.
   *
   * @return {@code true} if this call performed the transition
   */
  public boolean markRefunded() {
    if (status == OrderStatus.refunded) {
      return false;
    }
    if (status != OrderStatus.paid && status != OrderStatus.fulfilled) {
      throw new IllegalOrderTransitionException(
          status.wireValue(), OrderStatus.refunded.wireValue());
    }
    this.status = OrderStatus.refunded;
    return true;
  }

  /**
   * INV-1 guard: whether this order is in a state where an {@link OwnershipGrant} may be created.
   * The grant use case consults ONLY this predicate — a {@code pending}/{@code failed} order never
   * produces a grant.
   */
  public boolean canGrant() {
    return status == OrderStatus.paid || status == OrderStatus.fulfilled;
  }

  public OrderId getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public String getReference() {
    return reference;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Money getSubtotal() {
    return subtotal;
  }

  public Money getFee() {
    return fee;
  }

  public Money getTotal() {
    return total;
  }

  public String getPaymentIntentId() {
    return paymentIntentId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public List<OrderLine> getLines() {
    return List.copyOf(lines);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
