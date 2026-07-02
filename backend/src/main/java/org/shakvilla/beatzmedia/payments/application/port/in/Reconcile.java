package org.shakvilla.beatzmedia.payments.application.port.in;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Input port for the provider↔internal convergence guarantees driven by the platform scheduler
 * (payments ADD §4.1, §8a). Both operations are safe to re-run — the scheduler may invoke them on
 * every tick and each is idempotent.
 *
 * <ul>
 *   <li>{@link #pollPendingTimeouts} — the timeout/retry poll (LLFR-PAYMENTS-01.3): drives
 *       {@code pending} intents forward when a webhook never arrives.
 *   <li>{@link #reconcileDaily} — the reconciliation compare (LLFR-PAYMENTS-01.4): flags mismatches
 *       between provider truth and our own records as discrepancies for finance review.
 * </ul>
 */
public interface Reconcile {

  /**
   * Re-query the provider for each {@code pending} intent older than {@code olderThan} and advance it:
   * provider {@code SETTLED} → settle (emit {@code PaymentSettled}); provider {@code FAILED} → fail
   * (emit {@code PaymentFailed}); still pending and older than {@code maxWindow} → {@code timeout}
   * (emit {@code PaymentFailed(reason=timeout)}). Idempotent: an already-terminal intent is skipped.
   *
   * @param olderThan minimum age before an intent is polled (avoids racing a just-initiated charge)
   * @param maxWindow age after which a still-pending intent is forced to {@code timeout}
   */
  void pollPendingTimeouts(Duration olderThan, Duration maxWindow);

  /**
   * Compare provider truth against our {@code payment_intent} records for the given UTC day and
   * record a {@link org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy} for each
   * mismatch (e.g. provider-settled but our intent is not settled). Idempotent per
   * {@code (intentId, kind, day)} so repeated passes over the same window are no-ops.
   *
   * <p><strong>Scope (WU-PAY-2):</strong> the double-entry ledger lands in WU-PAY-3, so this compares
   * provider settlement against the intent's own status; the provider-vs-ledger-credit comparison
   * (INV-6) is completed once {@code ledger_entry} exists.
   *
   * @param day the UTC date whose intents to reconcile
   * @return a summary of the pass
   */
  ReconciliationReport reconcileDaily(LocalDate day);
}
