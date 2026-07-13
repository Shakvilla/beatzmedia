package org.shakvilla.beatzmedia.payments.adapter.in.job;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.shakvilla.beatzmedia.payments.application.service.HandleCashoutWebhookService;
import org.shakvilla.beatzmedia.payments.domain.WithdrawalId;
import org.shakvilla.beatzmedia.platform.application.port.in.ScheduledJob;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;

/**
 * Payout reconciliation job (WU-PAY-7), registered on the platform scheduler's {@code
 * payments.payout-recon} tick (every 30s). The fallback for a Redde cashout webhook that never
 * arrives: it sweeps withdrawals still {@code SENT} past a poll-after window and confirms each by an
 * authenticated pull-back — the payout analog of {@link PaymentReconJob}'s timeout poll.
 *
 * <p>Each withdrawal is confirmed in its own {@code REQUIRES_NEW} boundary ({@link
 * HandleCashoutWebhookService#confirmSent}, invoked across the bean boundary), so one unreachable
 * pull can't poison the sweep, and the confirm is idempotent (payout_event UNIQUE + exactly-once
 * ledger header), so re-running every tick is safe. The registry guarantees at-most-one execution
 * per tick via a Postgres advisory lock (single-flight across instances).
 */
@ApplicationScoped
public class PayoutReconJob implements ScheduledJob {

  private final HandleCashoutWebhookService confirmations;
  private final Clock clock;
  private final Duration pollAfter;
  private final int batchLimit;

  @Inject
  public PayoutReconJob(
      HandleCashoutWebhookService confirmations,
      Clock clock,
      @ConfigProperty(name = "beatz.payout.recon.poll-after", defaultValue = "2m")
          Duration pollAfter,
      @ConfigProperty(name = "beatz.payout.recon.batch-limit", defaultValue = "100")
          int batchLimit) {
    this.confirmations = confirmations;
    this.clock = clock;
    this.pollAfter = pollAfter;
    this.batchLimit = batchLimit;
  }

  @Override
  public String jobName() {
    return "payments.payout-recon";
  }

  @Override
  public void runOnce() {
    Instant sentBefore = clock.now().minus(pollAfter);
    List<WithdrawalId> candidates = confirmations.readSentCandidates(sentBefore, batchLimit);
    for (WithdrawalId wid : candidates) {
      confirmations.confirmSent(wid);
    }
  }
}
