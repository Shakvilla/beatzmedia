package org.shakvilla.beatzmedia.payments.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A creator's cash-out destination (MoMo or bank). Aggregate root (payments ADD §3). Exactly one
 * default per account is enforced by a partial unique index in V704; this type carries the {@code
 * isDefault} flag and the domain rule that the first method added becomes the default. Framework-free
 * (no Jakarta/Hibernate).
 */
public final class PayoutMethod {

  private final PayoutMethodId id;
  private final AccountId accountId;
  private final MethodKind kind;
  private final String label;
  private final String detail;
  private boolean isDefault;
  private final Instant createdAt;

  private PayoutMethod(
      PayoutMethodId id,
      AccountId accountId,
      MethodKind kind,
      String label,
      String detail,
      boolean isDefault,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.label = requireText(label, "label");
    this.detail = requireText(detail, "detail");
    this.isDefault = isDefault;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    if (kind == MethodKind.card) {
      throw new IllegalArgumentException("a card is not a valid payout destination");
    }
  }

  /**
   * Create a new payout method for a creator. {@code makeDefault} should be true when the account
   * has no existing methods (the first method is the default) or the caller explicitly defaults it.
   */
  public static PayoutMethod create(
      String id,
      AccountId accountId,
      MethodKind kind,
      String label,
      String detail,
      boolean makeDefault,
      Instant createdAt) {
    return new PayoutMethod(
        new PayoutMethodId(id), accountId, kind, label, detail, makeDefault, createdAt);
  }

  /** Reconstitute from persistence without re-running creation rules. */
  public static PayoutMethod reconstitute(
      PayoutMethodId id,
      AccountId accountId,
      MethodKind kind,
      String label,
      String detail,
      boolean isDefault,
      Instant createdAt) {
    return new PayoutMethod(id, accountId, kind, label, detail, isDefault, createdAt);
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

  public boolean isDefault() {
    return isDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
