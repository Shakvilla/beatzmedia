package org.shakvilla.beatzmedia.payments.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.shakvilla.beatzmedia.payments.application.port.in.Reconcile;
import org.shakvilla.beatzmedia.payments.application.port.in.ReconciliationReport;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway;
import org.shakvilla.beatzmedia.payments.application.port.out.PaymentGateway.ProviderStatus;
import org.shakvilla.beatzmedia.payments.domain.DiscrepancyKind;
import org.shakvilla.beatzmedia.payments.domain.PaymentEventType;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntent;
import org.shakvilla.beatzmedia.payments.domain.PaymentIntentStatus;
import org.shakvilla.beatzmedia.payments.domain.ProviderException;
import org.shakvilla.beatzmedia.payments.domain.ReconciliationDiscrepancy;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service implementing {@link Reconcile} — the timeout poll (LLFR-PAYMENTS-01.3) and the
 * daily reconciliation compare (LLFR-PAYMENTS-01.4). Both re-derive outcomes from provider truth via
 * {@link PaymentGateway#queryStatus} and are safe to re-run.
 *
 * <p><strong>Transaction shape.</strong> This orchestrator is intentionally <em>not</em>
 * {@code @Transactional}: it reads candidate intents in a short transaction ({@link ReconciliationStore}),
 * calls the provider outside any transaction (no DB lock held across the network), then applies each
 * outcome in its own transaction via {@link PaymentSettlementService} / {@link ReconciliationStore}.
 * A failure on one intent (e.g. an unreachable rail) is logged and skipped so it cannot roll back or
 * block the rest of the batch — the intent stays {@code pending} and is retried next tick.
 */
@ApplicationScoped
public class ReconcileService implements Reconcile {

  private static final Logger LOG = Logger.getLogger(ReconcileService.class);

  private final ReconciliationStore store;
  private final PaymentGateway gateway;
  private final PaymentSettlementService settlement;
  private final IdGenerator ids;
  private final Clock clock;

  @Inject
  public ReconcileService(
      ReconciliationStore store,
      PaymentGateway gateway,
      PaymentSettlementService settlement,
      IdGenerator ids,
      Clock clock) {
    this.store = store;
    this.gateway = gateway;
    this.settlement = settlement;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  public void pollPendingTimeouts(Duration olderThan, Duration maxWindow) {
    Instant now = clock.now();
    Instant cutoff = now.minus(olderThan);
    for (PaymentIntent intent : store.loadPendingOlderThan(cutoff)) {
      try {
        pollOne(intent, now, maxWindow);
      } catch (RuntimeException e) {
        // Isolate a poison intent: log and move on so the rest of the batch still progresses.
        LOG.warnf(e, "Timeout poll skipped intent %s after an error", intent.getId());
      }
    }
  }

  private void pollOne(PaymentIntent intent, Instant now, Duration maxWindow) {
    String providerRef = intent.getProviderRef();
    boolean pastMaxWindow = Duration.between(intent.getCreatedAt(), now).compareTo(maxWindow) > 0;

    if (providerRef == null || providerRef.isBlank()) {
      // Never accepted by a rail yet still pending — only the max-window fallback applies.
      if (pastMaxWindow) {
        settlement.timeout(intent.getId());
      }
      return;
    }

    ProviderStatus status;
    try {
      status = gateway.queryStatus(intent.getProvider(), providerRef);
    } catch (ProviderException e) {
      // Rail unreachable: leave pending and retry next tick (do not time out on a transient error).
      LOG.warnf("queryStatus failed for intent %s: %s — leaving pending", intent.getId(),
          e.getMessage());
      return;
    }

    switch (status.outcome()) {
      case SETTLED -> settlement.settle(intent.getId(), providerRef);
      case FAILED -> settlement.fail(intent.getId(), reasonOr(status.reason(), "declined"));
      case PENDING -> {
        if (pastMaxWindow) {
          settlement.timeout(intent.getId());
        }
      }
    }
  }

  @Override
  public ReconciliationReport reconcileDaily(LocalDate day) {
    Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant now = clock.now();

    int scanned = 0;
    int recorded = 0;
    for (PaymentIntent intent : store.loadForReconciliation(from, to)) {
      ProviderStatus status;
      try {
        status = gateway.queryStatus(intent.getProvider(), intent.getProviderRef());
      } catch (ProviderException e) {
        LOG.warnf("Reconciliation skipped intent %s: queryStatus failed: %s", intent.getId(),
            e.getMessage());
        continue;
      }
      scanned++;

      DiscrepancyKind kind = classify(intent.getStatus(), status.outcome());
      if (kind == null) {
        continue;
      }
      ReconciliationDiscrepancy discrepancy =
          ReconciliationDiscrepancy.of(
              ids.newId(),
              intent.getId(),
              intent.getOrderRef().value(),
              kind,
              intent.getAmount().minor(),
              status.outcome().name(),
              intent.getStatus().name(),
              day.toString(),
              now);
      if (store.record(discrepancy)) {
        recorded++;
      }
    }
    LOG.infof("Reconciliation for %s: scanned=%d, discrepancies=%d", day, scanned, recorded);
    return new ReconciliationReport(day, scanned, recorded);
  }

  /**
   * Compare our intent status with the provider's <em>definitive</em> outcome. A {@code PENDING}
   * provider response is inconclusive (the rail has not given a terminal answer) and never a
   * discrepancy.
   *
   * @return the discrepancy kind, or {@code null} if the records agree / the provider is inconclusive
   */
  private static DiscrepancyKind classify(
      PaymentIntentStatus intentStatus, PaymentEventType providerOutcome) {
    return switch (providerOutcome) {
      case SETTLED ->
          intentStatus == PaymentIntentStatus.settled
              ? null
              : DiscrepancyKind.PROVIDER_SETTLED_INTENT_NOT;
      case FAILED ->
          intentStatus == PaymentIntentStatus.settled
              ? DiscrepancyKind.PROVIDER_FAILED_INTENT_SETTLED
              : null;
      case PENDING -> null;
    };
  }

  private static String reasonOr(String reason, String fallback) {
    return (reason == null || reason.isBlank()) ? fallback : reason;
  }
}
