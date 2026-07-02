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
   * The provider reports the charge SETTLED, but our {@code payment_intent} is not {@code settled}
   * (e.g. a lost/never-delivered webhook the poll also missed). We owe a settlement we have not
   * recorded — finance must reconcile.
   */
  PROVIDER_SETTLED_INTENT_NOT,

  /**
   * Our {@code payment_intent} is {@code settled}, but the provider does not report it settled (e.g.
   * a spoofed/duplicated event, or a provider-side reversal). We may have granted value the rail did
   * not fund — a risk signal.
   */
  INTENT_SETTLED_PROVIDER_NOT,

  /**
   * The provider reports the charge FAILED, but our {@code payment_intent} is {@code settled} — a
   * potential over-grant that finance must review.
   */
  PROVIDER_FAILED_INTENT_SETTLED
}
