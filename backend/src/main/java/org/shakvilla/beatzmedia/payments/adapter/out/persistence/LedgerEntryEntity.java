package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code ledger_entry} table (V703). Domain types carry no ORM annotations.
 * {@code amount_minor} is always positive; {@code direction} ({@code DEBIT|CREDIT}) carries the sign.
 * The DB {@code assert_txn_balanced} deferred trigger enforces Σ DEBIT = Σ CREDIT per {@code txn_id}
 * at commit (INV-6). Payments ADD §5.2.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "txn_id", nullable = false)
  public String txnId;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "direction", nullable = false)
  public String direction;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "ref_type", nullable = false)
  public String refType;

  @Column(name = "ref_id", nullable = false)
  public String refId;

  @Column(name = "cleared_at")
  public Instant clearedAt;

  @Column(name = "posted_at", nullable = false)
  public Instant postedAt;
}
