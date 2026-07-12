package org.shakvilla.beatzmedia.admin.application.port.out;

import java.util.List;
import java.util.Optional;

import org.shakvilla.beatzmedia.admin.domain.RiskSignal;

/**
 * Output port for {@link RiskSignal} persistence (owns {@code risk_signal}; this module's table
 * only). Implemented by a JPA adapter in {@code adapter.out.persistence}. Admin ADD §4.2 / §7
 * (LLFR-ADMIN-07.1).
 */
public interface RiskSignalRepository {

  /** All risk signals ordered newest-first (the frontend paginates client-side). */
  List<RiskSignal> list();

  /** Loads a single signal, or empty if not found. */
  Optional<RiskSignal> findById(String signalId);

  /** Upsert: persists the signal's current state. */
  void save(RiskSignal signal);

  /** Count of {@code open} signals — the {@code fraudFlags} KPI on the risk board. */
  long countOpen();
}
