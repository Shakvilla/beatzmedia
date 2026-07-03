package org.shakvilla.beatzmedia.payments.domain;

/**
 * The four kinds of double-entry ledger account (payments ADD §3). Accounts are partitioned by kind;
 * money flows in via {@code PROVIDER_CLEARING}, accrues to {@code CREATOR_PAYABLE} and
 * {@code PLATFORM_REVENUE}, and flows out via {@code PAYOUT_CLEARING}. Framework-free.
 *
 * <table>
 *   <caption>Account kinds</caption>
 *   <tr><th>Kind</th><th>Owner</th><th>Normal balance</th></tr>
 *   <tr><td>{@code PROVIDER_CLEARING}</td><td>one per provider</td><td>debit</td></tr>
 *   <tr><td>{@code CREATOR_PAYABLE}</td><td>creator account</td><td>credit</td></tr>
 *   <tr><td>{@code PLATFORM_REVENUE}</td><td>singleton</td><td>credit</td></tr>
 *   <tr><td>{@code PAYOUT_CLEARING}</td><td>singleton (WU-PAY-4)</td><td>debit</td></tr>
 * </table>
 */
public enum LedgerAccountKind {
  PROVIDER_CLEARING,
  CREATOR_PAYABLE,
  PLATFORM_REVENUE,
  PAYOUT_CLEARING;

  /** Wire/DB token (lower-snake), e.g. {@code provider_clearing}. */
  public String wire() {
    return name().toLowerCase();
  }

  /** Parse a wire/DB token (e.g. {@code creator_payable}) to the enum. */
  public static LedgerAccountKind fromWire(String value) {
    if (value == null) {
      throw new IllegalArgumentException("ledger account kind must not be null");
    }
    return valueOf(value.trim().toUpperCase());
  }
}
