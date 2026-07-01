package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

import org.shakvilla.beatzmedia.platform.domain.Money;

/**
 * Aggregate root for a single attempt to charge a fan against a payment rail. Framework-free (no
 * Jakarta/Quarkus/Hibernate). Money is held as a {@link Money} value object (integer minor units,
 * INV-11).
 *
 * <p><strong>Lifecycle (payments ADD §8, WU-PAY-1 owns entry state {@code pending}):</strong>
 *
 * <pre>
 *   [*] --> pending
 *   pending --> settled : webhook/poll confirms   (WU-PAY-2)
 *   pending --> failed  : provider error / rejects
 *   pending --> timeout : maxWindow elapsed        (WU-PAY-2)
 * </pre>
 *
 * <p><strong>INV-1 (ownership-on-payment):</strong> nothing downstream may grant value until this
 * intent reaches {@code settled}; the aggregate never auto-settles — settlement is an explicit,
 * guarded transition.
 *
 * <p><strong>Idempotency:</strong> each intent carries the {@code idempotencyKey} that created it
 * plus a {@code requestFingerprint} (a stable hash of the initiating request). Replaying the same
 * key with the same fingerprint returns this same intent; a different fingerprint is a conflict
 * (payments ADD §9.2).
 */
public final class PaymentIntent {

  private final String id;
  private final AccountId accountId;
  private final OrderRef orderRef;
  private final Money amount;
  private final Provider provider;
  private final MethodKind methodKind;
  private String providerRef;
  private PaymentIntentStatus status;
  private String failureReason;
  private final String idempotencyKey;
  private final String requestFingerprint;
  private final Instant createdAt;
  private Instant updatedAt;

  private PaymentIntent(
      String id,
      AccountId accountId,
      OrderRef orderRef,
      Money amount,
      Provider provider,
      MethodKind methodKind,
      String providerRef,
      PaymentIntentStatus status,
      String failureReason,
      String idempotencyKey,
      String requestFingerprint,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.accountId = accountId;
    this.orderRef = orderRef;
    this.amount = amount;
    this.provider = provider;
    this.methodKind = methodKind;
    this.providerRef = providerRef;
    this.status = status;
    this.failureReason = failureReason;
    this.idempotencyKey = idempotencyKey;
    this.requestFingerprint = requestFingerprint;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /**
   * Factory for a brand-new charge attempt. Starts in {@code pending}. Amount must be non-negative
   * (a zero amount is allowed for future ₵0 flows but never negative). The {@code providerRef} is
   * initially null and set by {@link #markInitiated(String, Instant)} once the rail accepts the
   * charge.
   */
  public static PaymentIntent create(
      String id,
      AccountId accountId,
      OrderRef orderRef,
      Money amount,
      PaymentMethodRef method,
      IdempotencyKey idempotencyKey,
      String requestFingerprint,
      Instant now) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(orderRef, "orderRef");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(requestFingerprint, "requestFingerprint");
    Objects.requireNonNull(now, "now");
    if (amount.minor() < 0) {
      throw new IllegalArgumentException("amount must not be negative: " + amount.minor());
    }
    return new PaymentIntent(
        id,
        accountId,
        orderRef,
        amount,
        method.provider(),
        method.kind(),
        null,
        PaymentIntentStatus.pending,
        null,
        idempotencyKey.value(),
        requestFingerprint,
        now,
        now);
  }

  /** Reconstitute from persistence. No validation/normalisation — trusts stored state. */
  public static PaymentIntent reconstitute(
      String id,
      AccountId accountId,
      OrderRef orderRef,
      Money amount,
      Provider provider,
      MethodKind methodKind,
      String providerRef,
      PaymentIntentStatus status,
      String failureReason,
      String idempotencyKey,
      String requestFingerprint,
      Instant createdAt,
      Instant updatedAt) {
    return new PaymentIntent(
        id, accountId, orderRef, amount, provider, methodKind, providerRef, status, failureReason,
        idempotencyKey, requestFingerprint, createdAt, updatedAt);
  }

  /**
   * Record the provider reference returned when the rail accepts the charge. Legal only while
   * {@code pending}; keeps status {@code pending} (settlement is async).
   */
  public void markInitiated(String providerRef, Instant now) {
    requirePending("attach provider reference");
    if (providerRef == null || providerRef.isBlank()) {
      throw new IllegalArgumentException("providerRef must not be blank");
    }
    this.providerRef = providerRef;
    this.updatedAt = now;
  }

  /**
   * Transition {@code pending → settled} (INV-1 downstream grant trigger). Idempotent no-op if
   * already {@code settled} with the same providerRef; illegal from any other terminal state.
   */
  public void markSettled(String providerRef, Instant now) {
    if (status == PaymentIntentStatus.settled) {
      return; // idempotent: duplicate settlement is a no-op
    }
    guardTransition(PaymentIntentStatus.settled);
    if (providerRef != null && !providerRef.isBlank()) {
      this.providerRef = providerRef;
    }
    this.status = PaymentIntentStatus.settled;
    this.updatedAt = now;
  }

  /** Transition {@code pending → failed} with a reason. Idempotent no-op if already failed. */
  public void markFailed(String reason, Instant now) {
    if (status == PaymentIntentStatus.failed) {
      return;
    }
    guardTransition(PaymentIntentStatus.failed);
    this.status = PaymentIntentStatus.failed;
    this.failureReason = reason;
    this.updatedAt = now;
  }

  /** Transition {@code pending → timeout} once the reconciliation max-window elapses (WU-PAY-2). */
  public void markTimedOut(Instant now) {
    if (status == PaymentIntentStatus.timeout) {
      return;
    }
    guardTransition(PaymentIntentStatus.timeout);
    this.status = PaymentIntentStatus.timeout;
    this.failureReason = "timeout";
    this.updatedAt = now;
  }

  private void guardTransition(PaymentIntentStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new IllegalTransitionException(
          "PaymentIntent " + id + " cannot transition " + status + " -> " + target);
    }
  }

  private void requirePending(String action) {
    if (status != PaymentIntentStatus.pending) {
      throw new IllegalTransitionException(
          "PaymentIntent " + id + " is " + status + "; cannot " + action);
    }
  }

  /**
   * True if the supplied request fingerprint matches the one that created this intent. Used to
   * detect same-key/different-body idempotency conflicts (payments ADD §9.2).
   */
  public boolean matchesFingerprint(String candidate) {
    return this.requestFingerprint.equals(candidate);
  }

  public String getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public OrderRef getOrderRef() {
    return orderRef;
  }

  public Money getAmount() {
    return amount;
  }

  public Provider getProvider() {
    return provider;
  }

  public MethodKind getMethodKind() {
    return methodKind;
  }

  public String getProviderRef() {
    return providerRef;
  }

  public PaymentIntentStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
