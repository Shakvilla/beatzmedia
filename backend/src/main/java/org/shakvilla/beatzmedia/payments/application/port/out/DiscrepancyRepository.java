package org.shakvilla.beatzmedia.payments.application.port.out;

import org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy;

/**
 * Output port for persisting reconciliation discrepancies for finance review (payments ADD §4.2,
 * WU-PAY-2 / LLFR-PAYMENTS-01.4). Owns only the payments module's {@code reconciliation_discrepancy}
 * table.
 */
public interface DiscrepancyRepository {

  /**
   * Record a reconciliation discrepancy. Returns {@code true} if it is newly recorded, or
   * {@code false} if a discrepancy with the same {@code (intentId, kind, asOfDay)} already exists —
   * so re-running the daily reconciliation over the same window records each distinct mismatch at
   * most once. The natural-key UNIQUE constraint is the durable backstop against races.
   */
  boolean record(ReconciliationDiscrepancy discrepancy);
}
