package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code "order"} table. Domain types carry no ORM annotations. Commerce ADD §5.2 /
 * migration V944. ({@code order} is a reserved word — the table name is quoted.)
 */
@Entity
@Table(name = "\"order\"")
public class OrderEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "reference", nullable = false, unique = true)
  public String reference;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "subtotal_minor", nullable = false)
  public long subtotalMinor;

  @Column(name = "fee_minor", nullable = false)
  public long feeMinor;

  @Column(name = "total_minor", nullable = false)
  public long totalMinor;

  @Column(name = "currency", nullable = false)
  public String currency;

  @Column(name = "payment_intent_id")
  public String paymentIntentId;

  @Column(name = "failure_reason")
  public String failureReason;

  @Column(name = "idempotency_key", nullable = false)
  public String idempotencyKey;

  @Column(name = "request_hash", nullable = false)
  public String requestHash;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  public List<OrderLineEntity> lines = new ArrayList<>();
}
