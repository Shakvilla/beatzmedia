package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A provider-delivered payment event (webhook/callback), recorded as the idempotency backstop for
 * {@code HandleProviderWebhook} (LLFR-PAYMENTS-01.2). Framework-free.
 *
 * <p><strong>Idempotency (INV — money side-effect POSTs):</strong> {@code providerEventId} carries a
 * UNIQUE constraint in the {@code payment_event} table. Recording an event whose id was already seen
 * fails the constraint (or is short-circuited by the store), so a duplicate/replayed webhook applies
 * the settlement transition <em>at most once</em> and emits exactly one {@code PaymentSettled}
 * (payments ADD §8a).
 *
 * <p>Only the fields needed to make the ingest idempotent + auditable are captured; the raw payload
 * is stored verbatim as a JSON string so a later reconciliation can re-derive settlement from
 * provider truth without re-parsing at ingest time.
 */
public final class PaymentEvent {

  private final String id;
  private final String intentId;
  private final String providerEventId;
  private final PaymentEventType type;
  private final String payload;
  private final Instant receivedAt;

  private PaymentEvent(
      String id,
      String intentId,
      String providerEventId,
      PaymentEventType type,
      String payload,
      Instant receivedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.intentId = Objects.requireNonNull(intentId, "intentId");
    this.providerEventId = Objects.requireNonNull(providerEventId, "providerEventId");
    this.type = Objects.requireNonNull(type, "type");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    if (providerEventId.isBlank()) {
      throw new IllegalArgumentException("providerEventId must not be blank");
    }
  }

  /** Record a freshly-received provider event bound to the intent it settles/fails. */
  public static PaymentEvent record(
      String id,
      String intentId,
      String providerEventId,
      PaymentEventType type,
      String payload,
      Instant receivedAt) {
    return new PaymentEvent(id, intentId, providerEventId, type, payload, receivedAt);
  }

  public String getId() {
    return id;
  }

  public String getIntentId() {
    return intentId;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public PaymentEventType getType() {
    return type;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
