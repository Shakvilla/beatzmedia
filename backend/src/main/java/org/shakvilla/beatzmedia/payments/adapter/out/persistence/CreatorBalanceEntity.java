package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code creator_balance} projection (V703). Derived from cleared/uncleared
 * {@code creator_payable} credits (INV-6/INV-8) and refreshed inside the same transaction as each
 * ledger posting so it never drifts. Payments ADD §3/§5.2.
 */
@Entity
@Table(name = "creator_balance")
public class CreatorBalanceEntity {

  @Id
  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "available_minor", nullable = false)
  public long availableMinor;

  @Column(name = "pending_minor", nullable = false)
  public long pendingMinor;

  @Column(name = "lifetime_minor", nullable = false)
  public long lifetimeMinor;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
