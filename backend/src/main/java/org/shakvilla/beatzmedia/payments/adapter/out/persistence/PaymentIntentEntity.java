package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code payment_intent} table. Domain types carry no ORM annotations. Payments
 * ADD §5.2 / V701 migration.
 */
@Entity
@Table(name = "payment_intent")
public class PaymentIntentEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "order_ref", nullable = false)
  public String orderRef;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  /** Values: mtn | telecel | airteltigo | card | bank */
  @Column(name = "provider", nullable = false)
  public String provider;

  /** Values: momo | bank | card */
  @Column(name = "method_kind", nullable = false)
  public String methodKind;

  @Column(name = "provider_ref")
  public String providerRef;

  /** Values: pending | settled | failed | timeout */
  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "failure_reason")
  public String failureReason;

  @Column(name = "idempotency_key", nullable = false)
  public String idempotencyKey;

  @Column(name = "request_fingerprint", nullable = false)
  public String requestFingerprint;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
