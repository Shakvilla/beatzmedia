package org.shakvilla.beatzmedia.payments.domain;

/**
 * The category of a reconciliation mismatch flagged for finance review (LLFR-PAYMENTS-01.4). Pure
 * Java, no framework imports.
 *
 * <p><strong>Scope note (WU-PAY-2).</strong> The double-entry ledger lands in WU-PAY-3, so this WU
 * reconciles provider-reported settlement against the {@code payment_intent} / {@code payment_event}
 * records the payments module owns <em>today</em>. The full provider-settlement-vs-ledger-credit
 * comparison (INV-6) is completed in/after WU-PAY-3 once {@code ledger_entry} rows exist; that adds
 * a {@code MISSING_LEDGER_CREDIT} kind here.
 */
public enum DiscrepancyKind {

  /**
   * The provider <em>definitively</em> reports the charge SETTLED, but our {@code payment_intent} is
   * not {@code settled} (e.g. a lost/never-delivered webhook the poll also missed, or an intent that
   * timed out before a late settlement). We owe a settlement we have not recorded — in ledger terms
   * (WU-PAY-3) this is the "provider-settled charge with no matching credit" of the
   * LLFR-PAYMENTS-01.4 acceptance criterion. Finance must reconcile.
   */
  PROVIDER_SETTLED_INTENT_NOT,

  /**
   * The provider <em>definitively</em> reports the charge FAILED, but our {@code payment_intent} is
   * {@code settled} — we may have granted value the rail did not fund (e.g. acted on a spoofed or
   * premature webhook). A potential over-grant that finance must review.
   *
   * <p>Note: a provider response of {@code PENDING} is treated as <em>inconclusive</em> (the rail has
   * not given a terminal answer), so it never produces a discrepancy — only a definitive provider
   * terminal status that conflicts with our record is flagged.
   */
  PROVIDER_FAILED_INTENT_SETTLED
}
