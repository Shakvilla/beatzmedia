package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A finance {@code AttentionItem} / risk signal recorded when the daily reconciliation
 * (LLFR-PAYMENTS-01.4) finds a mismatch between provider settlement records and the payments
 * module's own {@code payment_intent} / {@code payment_event} records. Framework-free.
 *
 * <p>Persisted to {@code reconciliation_discrepancy} for finance review. Idempotent on
 * {@code (intent_id, kind, as_of_day)} so re-running the daily job over the same window records each
 * distinct mismatch <strong>once</strong> (the store upserts / ignores on conflict). Money is minor
 * units (INV-11); {@code detail} is short, non-PII text.
 */
public final class ReconciliationDiscrepancy {

  private final String id;
  private final String intentId;
  private final String orderRef;
  private final DiscrepancyKind kind;
  private final long amountMinor;
  private final String providerStatus;
  private final String intentStatus;
  private final String asOfDay; // ISO-8601 date (yyyy-MM-dd) of the reconciliation window
  private final Instant detectedAt;

  private ReconciliationDiscrepancy(
      String id,
      String intentId,
      String orderRef,
      DiscrepancyKind kind,
      long amountMinor,
      String providerStatus,
      String intentStatus,
      String asOfDay,
      Instant detectedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.intentId = Objects.requireNonNull(intentId, "intentId");
    this.orderRef = Objects.requireNonNull(orderRef, "orderRef");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.providerStatus = Objects.requireNonNull(providerStatus, "providerStatus");
    this.intentStatus = Objects.requireNonNull(intentStatus, "intentStatus");
    this.asOfDay = Objects.requireNonNull(asOfDay, "asOfDay");
    this.detectedAt = Objects.requireNonNull(detectedAt, "detectedAt");
    this.amountMinor = amountMinor;
  }

  public static ReconciliationDiscrepancy of(
      String id,
      String intentId,
      String orderRef,
      DiscrepancyKind kind,
      long amountMinor,
      String providerStatus,
      String intentStatus,
      String asOfDay,
      Instant detectedAt) {
    return new ReconciliationDiscrepancy(
        id, intentId, orderRef, kind, amountMinor, providerStatus, intentStatus, asOfDay,
        detectedAt);
  }

  public String getId() {
    return id;
  }

  public String getIntentId() {
    return intentId;
  }

  public String getOrderRef() {
    return orderRef;
  }

  public DiscrepancyKind getKind() {
    return kind;
  }

  public long getAmountMinor() {
    return amountMinor;
  }

  public String getProviderStatus() {
    return providerStatus;
  }

  public String getIntentStatus() {
    return intentStatus;
  }

  public String getAsOfDay() {
    return asOfDay;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }
}
