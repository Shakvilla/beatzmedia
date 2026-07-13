package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code payout_txn} table (V704). Payments ADD §5.2. */
@Entity
@Table(name = "payout_txn")
public class PayoutTxnEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "batch_id", nullable = false)
  public String batchId;

  @Column(name = "withdrawal_id", nullable = false)
  public String withdrawalId;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "provider_ref")
  public String providerRef;

  // Nullable (V967): a SENT txn has no ledger trace yet; set when confirmed-settled at webhook time.
  @Column(name = "disburse_txn_id")
  public String disburseTxnId;

  @Column(name = "paid_at", nullable = false)
  public Instant paidAt;
}
