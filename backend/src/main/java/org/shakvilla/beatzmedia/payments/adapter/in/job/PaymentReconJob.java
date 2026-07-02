package org.shakvilla.beatzmedia.payments.adapter.in.job;

import java.time.Duration;
import java.time.ZoneOffset;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.payments.application.port.in.Reconcile;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Payments reconciliation job, registered on the platform scheduler's {@code payments.payment-recon}
 * tick (every 30s — payments ADD §5.2). Per the platform scheduler design that tick drives both the
 * timeout poll and reconciliation, so a single {@link ScheduledJob} bean does both here:
 *
 * <ol>
 *   <li>{@link Reconcile#pollPendingTimeouts} — advance pending intents whose webhook never arrived
 *       (LLFR-PAYMENTS-01.3);
 *   <li>{@link Reconcile#reconcileDaily} — compare provider truth against our records for the current
 *       UTC day and record discrepancies for finance review (LLFR-PAYMENTS-01.4).
 * </ol>
 *
 * <p>Both operations are idempotent, so re-running each tick is safe; the registry already guarantees
 * at-most-one execution per tick via a Postgres advisory lock (single-flight across instances). The
 * reconciliation compare keys discrepancies per {@code (intentId, kind, day)}, so running it on every
 * tick records each distinct mismatch at most once per day.
 */
@ApplicationScoped
public class PaymentReconJob implements ScheduledJob {

  private final Reconcile reconcile;
  private final Clock clock;
  private final Duration pollAfter;
  private final Duration maxWindow;

  @Inject
  public PaymentReconJob(
      Reconcile reconcile,
      Clock clock,
      @ConfigProperty(name = "beatz.payment.timeout.poll-after", defaultValue = "5m")
          Duration pollAfter,
      @ConfigProperty(name = "beatz.payment.timeout.max-window", defaultValue = "30m")
          Duration maxWindow) {
    this.reconcile = reconcile;
    this.clock = clock;
    this.pollAfter = pollAfter;
    this.maxWindow = maxWindow;
  }

  @Override
  public String jobName() {
    return "payments.payment-recon";
  }

  @Override
  public void runOnce() {
    reconcile.pollPendingTimeouts(pollAfter, maxWindow);
    reconcile.reconcileDaily(clock.today(ZoneOffset.UTC));
  }
}
