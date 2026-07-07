package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.SaleFact;

/**
 * Output port: append-only staging store for {@link SaleFact} rows, owned exclusively by analytics
 * (its own table — never a commerce/payments table). Populated by the {@code SaleRecorded} event
 * observer; consumed and marked processed by the sales {@code RollupJob}. Analytics ADD §4.1
 * ({@code SettledSalesSource}).
 */
public interface SaleFactRepository {

  /** Append a fact (idempotent on {@code id} — a redelivered event is a benign no-op). */
  void append(SaleFact fact);

  /** All not-yet-rolled-up facts, oldest first. */
  List<SaleFact> findUnprocessed();

  /** Mark the given fact ids processed (upserted into {@code sales_rollup}). */
  void markProcessed(List<String> factIds);
}
