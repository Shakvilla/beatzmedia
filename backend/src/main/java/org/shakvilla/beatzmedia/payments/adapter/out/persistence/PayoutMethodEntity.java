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

  // Structured destination columns (V967, WU-PAY-7). Nullable; each kind uses only its own subset.
  @Column(name = "network")
  public String network; // momo only (mtn/telecel/airteltigo)

  @Column(name = "wallet_number")
  public String walletNumber; // momo only

  @Column(name = "bank_name")
  public String bankName; // bank only

  @Column(name = "bank_code")
  public String bankCode; // bank only (a GhanaBankCode token)

  @Column(name = "account_name")
  public String accountName; // bank only

  @Column(name = "account_number")
  public String accountNumber; // bank only

  @Column(name = "is_default", nullable = false)
  public boolean isDefault;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
