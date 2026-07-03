package org.shakvilla.beatzmedia.payments.domain;

/**
 * Read-model projection of a creator's derived balance (payments ADD §3, INV-6/INV-8). All fields are
 * integer minor units (INV-11). Framework-free.
 *
 * <ul>
 *   <li>{@code availableMinor} — Σ cleared {@code creator_payable} CREDIT − Σ cleared cash-out DEBIT;
 *       the amount the creator can withdraw (INV-8).
 *   <li>{@code pendingMinor} — Σ uncleared {@code creator_payable} CREDIT; earnings not yet cleared.
 *   <li>{@code lifetimeMinor} — Σ all {@code creator_payable} CREDIT ever; gross lifetime earnings.
 * </ul>
 */
public record CreatorBalance(
    AccountId accountId, long availableMinor, long pendingMinor, long lifetimeMinor) {

  public CreatorBalance {
    if (accountId == null) {
      throw new IllegalArgumentException("accountId must not be null");
    }
  }

  /** A zeroed balance for a creator with no ledger activity yet. */
  public static CreatorBalance zero(AccountId accountId) {
    return new CreatorBalance(accountId, 0L, 0L, 0L);
  }
}
