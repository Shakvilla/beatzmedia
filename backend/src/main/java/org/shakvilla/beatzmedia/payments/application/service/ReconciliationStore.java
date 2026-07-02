package org.shakvilla.beatzmedia.payments.application.service;

import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.out.DiscrepancyRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy;

/**
 * Short-transaction database boundary for the {@link ReconcileService} orchestrator. Each method runs
 * in its own transaction so the orchestrator can call the provider (a network round-trip) <em>between</em>
 * DB reads and writes without holding a transaction open across the wire, and so recording one
 * discrepancy is isolated from the next.
 *
 * <p>These are deliberately fine-grained, framework-facing helpers; the reconciliation policy (which
 * intents to poll, how to classify a mismatch) lives in {@link ReconcileService}.
 */
@ApplicationScoped
public class ReconciliationStore {

  private final PaymentRepository intents;
  private final DiscrepancyRepository discrepancies;

  @Inject
  public ReconciliationStore(PaymentRepository intents, DiscrepancyRepository discrepancies) {
    this.intents = intents;
    this.discrepancies = discrepancies;
  }

  /** Pending intents at or older than {@code cutoff} (timeout poll candidates, LLFR-PAYMENTS-01.3). */
  @Transactional
  public List<PaymentIntent> loadPendingOlderThan(Instant cutoff) {
    return intents.findPendingOlderThan(cutoff);
  }

  /** Intents with a provider ref created within {@code [from, to)} (reconciliation candidates, 01.4). */
  @Transactional
  public List<PaymentIntent> loadForReconciliation(Instant from, Instant to) {
    return intents.findForReconciliation(from, to);
  }

  /**
   * Record a discrepancy; returns {@code true} only if newly recorded (idempotent per
   * {@code (intentId, kind, asOfDay)}).
   */
  @Transactional
  public boolean record(ReconciliationDiscrepancy discrepancy) {
    return discrepancies.record(discrepancy);
  }
}
