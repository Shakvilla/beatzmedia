package org.shakvilla.beatzmedia.payments.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA entity for the {@code withdrawal_request} table (V704). Payments ADD §5.2. */
@Entity
@Table(name = "withdrawal_request")
public class WithdrawalRequestEntity {

  @Id
  @Column(name = "id", nullable = false)
  public String id;

  @Column(name = "account_id", nullable = false)
  public String accountId;

  @Column(name = "amount_minor", nullable = false)
  public long amountMinor;

  /**
   * The informational rail-side cash-out cost (provider charge). Surfaced to the creator
   * pre-confirmation; NOT posted to the ledger as platform revenue — the reservation debits the gross
   * (ADR-25 / review F2).
   */
  @Column(name = "fee_minor", nullable = false)
  public long feeMinor;

  @Column(name = "method_id", nullable = false)
  public String methodId;

  @Column(name = "status", nullable = false)
  public String status;

  @Column(name = "reserve_txn_id", nullable = false)
  public String reserveTxnId;

  @Column(name = "idempotency_key", nullable = false)
  public String idempotencyKey;

  @Column(name = "requested_at", nullable = false)
  public Instant requestedAt;
}
