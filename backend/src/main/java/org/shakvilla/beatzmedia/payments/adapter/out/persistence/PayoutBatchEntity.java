package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code payout_batch} table (V704). Payments ADD §5.2. */
@Entity
@Table(name = "payout_batch")
public class PayoutBatchEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "run_by", nullable = false)
  public String runBy;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "total_minor", nullable = false)
  public long totalMinor;

  @Column(name = "count", nullable = false)
  public int count;

  @Column(name = "run_at", nullable = false)
  public Instant runAt;
}
