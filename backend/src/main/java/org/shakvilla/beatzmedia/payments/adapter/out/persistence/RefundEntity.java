package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code refund} table (V705, WU-PAY-5). Payments ADD §5.2. */
@Entity
@Table(name = "refund")
public class RefundEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "dispute_id", nullable = false)
  public String disputeId;

  @Column(name = "payment_intent_id", nullable = false)
  public String paymentIntentId;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "reason")
  public String reason;

  @Column(name = "clawback_txn_id", nullable = false)
  public String clawbackTxnId;

  @Column(name = "at", nullable = false)
  public Instant at;
}
