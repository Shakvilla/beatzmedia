package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Dispute aggregate root (payments ADD §3 / §8, HLFR-PAYMENTS-04). A dispute is opened against a
 * settled order — by a fan refund request, or by a provider chargeback delivered through the
 * signature-verified webhook infra (WU-PAY-2). It holds the order reference and the settled payment
 * intent id (the clawback anchor), the disputed amount, its {@link DisputeStatus} state machine, and
 * a {@code chargeback} flag (a chargeback's provider-driven open→lost/won outcome maps onto the same
 * status machine — LOST ⇒ refund path, WON ⇒ reject path).
 *
 * <p>Framework-free (no Jakarta/Hibernate). Money is minor units (INV-11). The aggregate NEVER moves
 * money or revokes ownership itself — it only guards the status transitions; the application service
 * (RefundDisputeService) posts the balanced clawback and emits {@code OrderRefunded} so commerce
 * revokes the grants (INV-9).
 */
public final class Dispute {

  private final DisputeId id;
  private final String orderRef;
  private final String paymentIntentId;
  private final String kind;
  private final String subject;
  private final String detail;
  private final Money amount;
  private final boolean chargeback;
  private DisputeStatus status;
  private final Instant openedAt;

  public Dispute(
      DisputeId id,
      String orderRef,
      String paymentIntentId,
      String kind,
      String subject,
      String detail,
      Money amount,
      boolean chargeback,
      DisputeStatus status,
      Instant openedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.orderRef = requireText(orderRef, "orderRef");
    this.paymentIntentId = requireText(paymentIntentId, "paymentIntentId");
    this.kind = requireText(kind, "kind");
    this.subject = subject == null ? "" : subject;
    this.detail = detail == null ? "" : detail;
    this.amount = Objects.requireNonNull(amount, "amount");
    this.chargeback = chargeback;
    this.status = Objects.requireNonNull(status, "status");
    this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
  }

  /**
   * Open a fresh dispute against a settled order (status {@code open}).
   *
   * @param paymentIntentId the settled intent id — the anchor the refund clawback reverses (INV-9)
   * @param chargeback true if this dispute originates from a provider chargeback (LOST forces refund)
   */
  public static Dispute open(
      DisputeId id,
      String orderRef,
      String paymentIntentId,
      String kind,
      String subject,
      String detail,
      Money amount,
      boolean chargeback,
      Instant openedAt) {
    return new Dispute(
        id, orderRef, paymentIntentId, kind, subject, detail, amount, chargeback,
        DisputeStatus.open, openedAt);
  }

  /**
   * Guard {@code open → refunded} (INV-9). A dispute may only be refunded from {@code open}; a
   * terminal ({@code refunded}/{@code rejected}) or {@code escalated} dispute cannot be refunded until
   * re-opened. Idempotent: refunding an already-{@code refunded} dispute is a no-op ({@code false}).
   *
   * @return {@code true} if THIS call performed the transition (the caller then posts the clawback)
   * @throws IllegalTransitionException from a non-{@code open}/{@code refunded} status
   */
  public boolean markRefunded() {
    if (status == DisputeStatus.refunded) {
      return false;
    }
    if (status != DisputeStatus.open) {
      throw new IllegalTransitionException(
          "dispute " + id + " cannot be refunded from status " + status.wire());
    }
    this.status = DisputeStatus.refunded;
    return true;
  }

  /** Guard {@code open → rejected} (admin rejects, no money moves). Idempotent on already-rejected. */
  public boolean markRejected() {
    if (status == DisputeStatus.rejected) {
      return false;
    }
    if (status != DisputeStatus.open) {
      throw new IllegalTransitionException(
          "dispute " + id + " cannot be rejected from status " + status.wire());
    }
    this.status = DisputeStatus.rejected;
    return true;
  }

  /** Guard {@code open → escalated} (admin escalates for review). Idempotent on already-escalated. */
  public boolean markEscalated() {
    if (status == DisputeStatus.escalated) {
      return false;
    }
    if (status != DisputeStatus.open) {
      throw new IllegalTransitionException(
          "dispute " + id + " cannot be escalated from status " + status.wire());
    }
    this.status = DisputeStatus.escalated;
    return true;
  }

  public DisputeId getId() {
    return id;
  }

  public String getOrderRef() {
    return orderRef;
  }

  public String getPaymentIntentId() {
    return paymentIntentId;
  }

  public String getKind() {
    return kind;
  }

  public String getSubject() {
    return subject;
  }

  public String getDetail() {
    return detail;
  }

  public Money getAmount() {
    return amount;
  }

  public boolean isChargeback() {
    return chargeback;
  }

  public DisputeStatus getStatus() {
    return status;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
