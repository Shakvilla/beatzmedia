package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code payout_method} table (V704). Payments ADD §5.2. */
@Entity
@Table(name = "payout_method")
public class PayoutMethodEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "label", nullable = false)
  public String label;

  @Column(name = "detail", nullable = false)
  public String detail;

  @Column(name = "is_default", nullable = false)
  public boolean isDefault;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
