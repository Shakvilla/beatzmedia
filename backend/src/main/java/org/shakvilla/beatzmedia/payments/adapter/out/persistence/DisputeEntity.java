package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code dispute} table (V705, WU-PAY-5). Payments ADD §5.2. */
@Entity
@Table(name = "dispute")
public class DisputeEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "order_ref", nullable = false)
  public String orderRef;

  @Column(name = "payment_intent_id", nullable = false)
  public String paymentIntentId;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "subject", nullable = false)
  public String subject;

  @Column(name = "detail", nullable = false)
  public String detail;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "is_chargeback", nullable = false)
  public boolean isChargeback;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "provider_case_id")
  public String providerCaseId;

  @Column(name = "opened_at", nullable = false)
  public Instant openedAt;
}
