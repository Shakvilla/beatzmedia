package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A creator's cash-out destination (MoMo or bank). Aggregate root (payments ADD §3). Exactly one
 * default per account is enforced by a partial unique index in V704; this type carries the {@code
 * isDefault} flag and the domain rule that the first method added becomes the default. Framework-free
 * (no Jakarta/Hibernate).
 *
 * <p>WU-PAY-7 adds the <strong>structured destination</strong> fields Redde's {@code /v1/cashout}
 * needs — {@code network}/{@code walletNumber} for MoMo, {@code bankCode}/{@code bankName}/{@code
 * accountName}/{@code accountNumber} for bank — replacing the opaque free-text {@code detail} for the
 * gateway call. {@code detail} remains a legacy display label. New methods are created from a
 * validated {@link PayoutDestination} (so completeness is checked once, up front); {@link
 * #toDestination()} rebuilds it for the disbursement call.
 */
public final class PayoutMethod {

  private final PayoutMethodId id;
  private final AccountId accountId;
  private final MethodKind kind;
  private final String label;
  private final String detail;
  // Structured destination columns (WU-PAY-7). Nullable: legacy rows (pre-V967) have none, and each
  // kind uses only its own subset. toDestination() rebuilds the validated PayoutDestination.
  private final Provider network; // momo only
  private final String walletNumber; // momo only
  private final String bankName; // bank only
  private final String bankCode; // bank only (a GhanaBankCode token)
  private final String accountName; // bank only
  private final String accountNumber; // bank only
  private boolean isDefault;
  private final Instant createdAt;

  private PayoutMethod(
      PayoutMethodId id,
      AccountId accountId,
      MethodKind kind,
      String label,
      String detail,
      Provider network,
      String walletNumber,
      String bankName,
      String bankCode,
      String accountName,
      String accountNumber,
      boolean isDefault,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.label = requireText(label, "label");
    this.detail = requireText(detail, "detail");
    this.network = network;
    this.walletNumber = walletNumber;
    this.bankName = bankName;
    this.bankCode = bankCode;
    this.accountName = accountName;
    this.accountNumber = accountNumber;
    this.isDefault = isDefault;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    if (kind == MethodKind.card) {
      throw new IllegalArgumentException("a card is not a valid payout destination");
    }
  }

  /**
   * Create a new payout method for a creator from a validated {@link PayoutDestination} — {@code
   * kind} is derived from the destination and the structured columns are flattened out of it, so a
   * new method always has a complete, valid destination. {@code makeDefault} should be true when the
   * account has no existing methods (the first method is the default) or the caller explicitly
   * defaults it.
   */
  public static PayoutMethod create(
      String id,
      AccountId accountId,
      String label,
      String detail,
      PayoutDestination destination,
      boolean makeDefault,
      Instant createdAt) {
    Objects.requireNonNull(destination, "destination");
    return switch (destination) {
      case PayoutDestination.Momo m ->
          new PayoutMethod(
              new PayoutMethodId(id),
              accountId,
              MethodKind.momo,
              label,
              detail,
              m.network(),
              m.walletNumber(),
              null,
              null,
              null,
              null,
              makeDefault,
              createdAt);
      case PayoutDestination.Bank b ->
          new PayoutMethod(
              new PayoutMethodId(id),
              accountId,
              MethodKind.bank,
              label,
              detail,
              null,
              null,
              b.bankName(),
              b.bankCode().name(),
              b.accountName(),
              b.accountNumber(),
              makeDefault,
              createdAt);
    };
  }

  /** Reconstitute from persistence without re-running creation rules (tolerates legacy null fields). */
  public static PayoutMethod reconstitute(
      PayoutMethodId id,
      AccountId accountId,
      MethodKind kind,
      String label,
      String detail,
      Provider network,
      String walletNumber,
      String bankName,
      String bankCode,
      String accountName,
      String accountNumber,
      boolean isDefault,
      Instant createdAt) {
    return new PayoutMethod(
        id,
        accountId,
        kind,
        label,
        detail,
        network,
        walletNumber,
        bankName,
        bankCode,
        accountName,
        accountNumber,
        isDefault,
        createdAt);
  }

  /**
   * Rebuild the structured {@link PayoutDestination} for a disbursement call.
   *
   * @throws IllegalStateException if this method predates the structured fields (a legacy row) or is
   *     otherwise incomplete — the caller (payout disburser) surfaces it as a failed disbursement
   *     rather than sending an under-specified cashout to the rail.
   */
  public PayoutDestination toDestination() {
    try {
      return switch (kind) {
        case momo -> new PayoutDestination.Momo(network, walletNumber);
        case bank ->
            new PayoutDestination.Bank(
                GhanaBankCode.of(bankCode), bankName, accountName, accountNumber);
        case card -> throw new IllegalStateException("a card is not a payout destination");
      };
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "payout method " + id + " has no valid structured destination (re-add it): " + e.getMessage(),
          e);
    }
  }

  /** Mark this method as the account default (the caller clears the prior default in the same txn). */
  public void makeDefault() {
    this.isDefault = true;
  }

  /** Clear the default flag (used when another method becomes the default). */
  public void clearDefault() {
    this.isDefault = false;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  public PayoutMethodId getId() {
    return id;
  }

  public AccountId getAccountId() {
    return accountId;
  }

  public MethodKind getKind() {
    return kind;
  }

  public String getLabel() {
    return label;
  }

  public String getDetail() {
    return detail;
  }

  public Provider getNetwork() {
    return network;
  }

  public String getWalletNumber() {
    return walletNumber;
  }

  public String getBankName() {
    return bankName;
  }

  public String getBankCode() {
    return bankCode;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
