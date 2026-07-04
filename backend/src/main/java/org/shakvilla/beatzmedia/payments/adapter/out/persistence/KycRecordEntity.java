package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code kyc_record} table (V704, INV-8). Payments ADD §5.2. */
@Entity
@Table(name = "kyc_record")
public class KycRecordEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "verified_at")
  public Instant verifiedAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
