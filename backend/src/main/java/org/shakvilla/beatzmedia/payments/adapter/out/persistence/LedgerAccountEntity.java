package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code ledger_account} table (V703). Domain types carry no ORM annotations.
 * Payments ADD §5.2. Ids are UUIDv7 strings; {@code kind} is the lower-snake wire token
 * ({@code provider_clearing|creator_payable|platform_revenue|payout_clearing}).
 */
@Entity
@Table(name = "ledger_account")
public class LedgerAccountEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "kind", nullable = false)
  public String kind;

  @Column(name = "owner_account_id")
  public String ownerAccountId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
