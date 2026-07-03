package org.shakvilla.beatzmedia.payments.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.out.PaymentRepository;
import org.shakvilla.beatzmedia.payments.domain.PaymentFailed;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentSettled;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Internal application collaborator that applies exactly one {@link PaymentIntent} settlement
 * transition and emits the corresponding domain event — shared by the webhook handler
 * (LLFR-PAYMENTS-01.2) and the timeout poll (LLFR-PAYMENTS-01.3) so both paths transition and
 * publish identically.
 *
 * <p><strong>Exactly-once (INV-1):</strong> each method re-loads the intent by id inside its own
 * transaction and skips if it is already terminal, so a webhook and the poll racing on the same
 * intent transition it at most once and fire exactly one event (whoever wins the guarded transition
 * fires; the loser observes a terminal status and no-ops). Events publish {@code AFTER_SUCCESS} of the
 * caller's transaction — the default {@code REQUIRED} propagation means a call from the already-
 * transactional webhook handler joins that transaction (event recording + transition + publish are
 * atomic), while a call from the (non-transactional) poll runs one transaction per intent so one bad
 * intent cannot roll back the rest of the batch.
 */
@ApplicationScoped
public class PaymentSettlementService {

  private final PaymentRepository repository;
  private final Clock clock;
  private final Event<PaymentSettled> settledEvent;
  private final Event<PaymentFailed> failedEvent;

  @Inject
  public PaymentSettlementService(
      PaymentRepository repository,
      Clock clock,
      Event<PaymentSettled> settledEvent,
      Event<PaymentFailed> failedEvent) {
    this.repository = repository;
    this.clock = clock;
    this.settledEvent = settledEvent;
    this.failedEvent = failedEvent;
  }

  /**
   * Transition {@code pending → settled} and emit {@link PaymentSettled}. No-op (returns
   * {@code false}) if the intent is unknown or already terminal.
   *
   * @return {@code true} if this call performed the transition
   */
  @Transactional
  public boolean settle(String intentId, String providerRef) {
    // Pessimistic row lock so two concurrent settlements for the SAME intent serialise: the loser
    // blocks until the winner commits pending → settled, then re-reads a terminal status and no-ops
    // — exactly one PaymentSettled is fired (finding F1 defense-in-depth; the ledger_posting claim in
    // LedgerPostingService is the primary DB-level exactly-once guard for the credit).
    PaymentIntent intent = repository.findByIdForUpdate(intentId).orElse(null);
    if (intent == null || intent.getStatus().isTerminal()) {
      return false;
    }
    intent.markSettled(providerRef, clock.now());
    repository.save(intent);
    settledEvent.fire(PaymentSettled.from(intent, intent.getUpdatedAt()));
    return true;
  }

  /**
   * Transition {@code pending → failed} with {@code reason} and emit {@link PaymentFailed}. No-op
   * (returns {@code false}) if the intent is unknown or already terminal.
   *
   * @return {@code true} if this call performed the transition
   */
  @Transactional
  public boolean fail(String intentId, String reason) {
    PaymentIntent intent = repository.findById(intentId).orElse(null);
    if (intent == null || intent.getStatus().isTerminal()) {
      return false;
    }
    intent.markFailed(reason, clock.now());
    repository.save(intent);
    failedEvent.fire(PaymentFailed.from(intent, intent.getUpdatedAt()));
    return true;
  }

  /**
   * Transition {@code pending → timeout} (max-window elapsed with no settlement) and emit
   * {@link PaymentFailed} with reason {@code "timeout"}. No-op (returns {@code false}) if the intent
   * is unknown or already terminal.
   *
   * @return {@code true} if this call performed the transition
   */
  @Transactional
  public boolean timeout(String intentId) {
    PaymentIntent intent = repository.findById(intentId).orElse(null);
    if (intent == null || intent.getStatus().isTerminal()) {
      return false;
    }
    intent.markTimedOut(clock.now());
    repository.save(intent);
    failedEvent.fire(PaymentFailed.from(intent, intent.getUpdatedAt()));
    return true;
  }
}
