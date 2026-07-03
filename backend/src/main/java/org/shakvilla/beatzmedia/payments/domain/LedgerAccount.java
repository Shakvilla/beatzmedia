package org.shakvilla.beatzmedia.payments.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A double-entry ledger account (payments ADD §3). Accounts are partitioned by {@link
 * LedgerAccountKind}. {@code CREATOR_PAYABLE} and {@code PROVIDER_CLEARING} accounts carry an
 * {@code ownerAccountId} (the creator, or the provider name); {@code PLATFORM_REVENUE} and
 * {@code PAYOUT_CLEARING} are shared singletons with no owner. Framework-free entity.
 */
public final class LedgerAccount {

  private final LedgerAccountId id;
  private final LedgerAccountKind kind;
  private final String ownerAccountId;

  private LedgerAccount(LedgerAccountId id, LedgerAccountKind kind, String ownerAccountId) {
    this.id = Objects.requireNonNull(id, "id");
    this.kind = Objects.requireNonNull(kind, "kind");
    this.ownerAccountId = ownerAccountId;
  }

  /** Create a per-owner account ({@code CREATOR_PAYABLE} or {@code PROVIDER_CLEARING}). */
  public static LedgerAccount owned(
      LedgerAccountId id, LedgerAccountKind kind, String ownerAccountId) {
    if (ownerAccountId == null || ownerAccountId.isBlank()) {
      throw new IllegalArgumentException("owned account requires a non-blank ownerAccountId");
    }
    if (kind != LedgerAccountKind.CREATOR_PAYABLE && kind != LedgerAccountKind.PROVIDER_CLEARING) {
      throw new IllegalArgumentException("owned account must be CREATOR_PAYABLE or PROVIDER_CLEARING");
    }
    return new LedgerAccount(id, kind, ownerAccountId);
  }

  /** Create a shared singleton account ({@code PLATFORM_REVENUE} or {@code PAYOUT_CLEARING}). */
  public static LedgerAccount singleton(LedgerAccountId id, LedgerAccountKind kind) {
    if (kind != LedgerAccountKind.PLATFORM_REVENUE && kind != LedgerAccountKind.PAYOUT_CLEARING) {
      throw new IllegalArgumentException("singleton account must be PLATFORM_REVENUE or PAYOUT_CLEARING");
    }
    return new LedgerAccount(id, kind, null);
  }

  /** Reconstitute from persistence without validation. */
  public static LedgerAccount reconstitute(
      LedgerAccountId id, LedgerAccountKind kind, String ownerAccountId) {
    return new LedgerAccount(id, kind, ownerAccountId);
  }

  public LedgerAccountId getId() {
    return id;
  }

  public LedgerAccountKind getKind() {
    return kind;
  }

  public Optional<String> getOwnerAccountId() {
    return Optional.ofNullable(ownerAccountId);
  }
}
