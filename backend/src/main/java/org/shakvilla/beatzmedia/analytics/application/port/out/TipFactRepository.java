package org.shakvilla.beatzmedia.analytics.application.port.out;

import java.util.List;

import org.shakvilla.beatzmedia.analytics.domain.TipFact;

/**
 * Output port: append-only staging store for {@link TipFact} rows, owned exclusively by analytics.
 * Populated by the {@code TipReceived} event observer; consumed and marked processed by the sales
 * {@code RollupJob} (tips share the {@code sales_rollup} table, ADD §7). Analytics ADD §4.1.
 */
public interface TipFactRepository {

  void append(TipFact fact);

  List<TipFact> findUnprocessed();

  void markProcessed(List<String> factIds);
}
