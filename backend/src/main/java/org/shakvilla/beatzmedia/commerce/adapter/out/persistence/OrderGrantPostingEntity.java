package org.shakvilla.beatzmedia.commerce.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code order_grant_posting} exactly-once claim header (migration V944). The
 * PRIMARY KEY on {@code orderId} is what makes the settlement→grant fan-out run exactly once per
 * order (INV-1) — mirrors the payments {@code ledger_posting} pattern (WU-PAY-3 / ADR-22).
 */
@Entity
@Table(name = "order_grant_posting")
public class OrderGrantPostingEntity {

  @Id
  @Column(name = "order_id", nullable = false)
  public String orderId;

  @Column(name = "posted_at", nullable = false)
  public Instant postedAt;
}
