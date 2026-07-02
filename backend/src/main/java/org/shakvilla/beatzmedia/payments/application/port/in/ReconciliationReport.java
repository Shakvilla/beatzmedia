package org.shakvilla.beatzmedia.payments.application.port.in;

import java.time.LocalDate;

/**
 * Summary of one {@link Reconcile#reconcileDaily} pass (LLFR-PAYMENTS-01.4): how many intents were
 * compared against provider truth and how many fresh discrepancies were recorded for finance review.
 * A read-model DTO — carries no domain types across the port boundary.
 *
 * @param day the reconciliation window (UTC date) that was scanned
 * @param scanned number of intents compared against the provider
 * @param discrepancies number of <em>new</em> discrepancies recorded this pass (idempotent per
 *     {@code (intentId, kind, day)} — re-runs over the same window add nothing)
 */
public record ReconciliationReport(LocalDate day, int scanned, int discrepancies) {}
