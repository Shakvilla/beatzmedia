package org.shakvilla.beatzmedia.payments.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView.DisputeSummary;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView.Kpis;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView.PendingPayoutSummary;
import org.shakvilla.beatzmedia.payments.application.port.in.FinanceOverviewView.ProviderMixEntry;
import org.shakvilla.beatzmedia.payments.application.port.in.GetFinanceOverview;
import org.shakvilla.beatzmedia.payments.application.port.in.ListPendingPayouts;
import org.shakvilla.beatzmedia.payments.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.payments.application.port.in.PendingPayoutView;
import org.shakvilla.beatzmedia.payments.application.port.out.DisputeRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository;
import org.shakvilla.beatzmedia.payments.application.port.out.LedgerRepository.FinanceAggregate;
import org.shakvilla.beatzmedia.payments.domain.Dispute;
import org.shakvilla.beatzmedia.payments.domain.FinanceRange;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.PlatformSettingsProvider;

/**
 * Read service for {@link GetFinanceOverview} (LLFR-ADMIN-05.1). Aggregates the payments module's own
 * ledger, payouts, and disputes into the admin finance dashboard shape. Read-only — no money moves,
 * nothing audited. Finance/super-admin scope is enforced at the inbound resource.
 *
 * <p><strong>What is real vs. honest-empty.</strong> Category A (real, backed by the ledger/payout/
 * dispute tables): {@code gmvMtd}, {@code gmvDelta}, {@code platformFee}, {@code feeTakePct}, {@code
 * payoutsDue}/{@code payoutsArtists}/{@code pendingPayouts}, and the open {@code disputes}. Category B
 * (honest-empty, no backing subsystem — same precedent as WU-ADM-1's health payload): {@code
 * momoFloat} (no provider-float/treasury feed) is {@code 0}, and {@code providerMix} (no
 * payment-provider volume analytics) is an empty list. Both are documented carryovers in the ADD.
 *
 * <p><strong>Window.</strong> {@code range} is a trailing window ({@code 24h}=1d, {@code 7d}, {@code
 * 30d}); {@code gmvMtd} is GMV over that window and {@code gmvDelta} its percentage change against the
 * immediately preceding window of equal length. Money is bare decimal cedis on this surface (see
 * {@link FinanceOverviewView}).
 */
@ApplicationScoped
public class GetFinanceOverviewService implements GetFinanceOverview {

  /** Cap on the open-disputes "needs attention" preview returned in the overview. */
  private static final int OPEN_DISPUTE_LIMIT = 20;

  private final LedgerRepository ledger;
  private final DisputeRepository disputes;
  private final ListPendingPayouts pendingPayouts;
  private final PlatformSettingsProvider settings;
  private final Clock clock;

  @Inject
  public GetFinanceOverviewService(
      LedgerRepository ledger,
      DisputeRepository disputes,
      ListPendingPayouts pendingPayouts,
      PlatformSettingsProvider settings,
      Clock clock) {
    this.ledger = ledger;
    this.disputes = disputes;
    this.pendingPayouts = pendingPayouts;
    this.settings = settings;
    this.clock = clock;
  }

  @Override
  @Transactional
  public FinanceOverviewView overview(FinanceRange range) {
    Instant until = clock.now();
    Instant since = until.minus(range.days(), ChronoUnit.DAYS);
    Instant priorSince = since.minus(range.days(), ChronoUnit.DAYS);

    // KPIs — ledger aggregates over the selected window and the immediately-preceding window.
    FinanceAggregate current = ledger.financeSince(since, until);
    long priorGmv = ledger.financeSince(priorSince, since).gmvMinor();
    int gmvDelta = percentDelta(current.gmvMinor(), priorGmv);
    int feeTakePct = settings.current().platformFeePct();

    // Payouts — reuse the payable-withdrawals use case; aggregate the KPI over the full set.
    List<PendingPayoutView> payable = pendingPayouts.list();
    long payoutsDueMinor = 0L;
    for (PendingPayoutView p : payable) {
      payoutsDueMinor += toMinor(p.amount().amount());
    }
    int payoutsArtists =
        (int) payable.stream().map(PendingPayoutView::artist).distinct().count();
    List<PendingPayoutSummary> pending =
        payable.stream()
            .map(
                p ->
                    new PendingPayoutSummary(
                        p.id(), p.artist(), p.amount().amount(), p.method(), p.status()))
            .toList();

    // Disputes — the open "needs attention" preview.
    List<DisputeSummary> openDisputes =
        disputes.findOpen(OPEN_DISPUTE_LIMIT).stream()
            .map(GetFinanceOverviewService::disputeSummary)
            .toList();

    Kpis kpis =
        new Kpis(
            cedis(current.gmvMinor()),
            gmvDelta,
            cedis(current.platformFeeMinor()),
            feeTakePct,
            cedis(payoutsDueMinor),
            payoutsArtists,
            // Category B (honest-empty): no provider-float/treasury feed exists yet.
            BigDecimal.ZERO.setScale(2));

    // Category B (honest-empty): no payment-provider volume analytics subsystem exists yet.
    List<ProviderMixEntry> providerMix = List.of();

    return new FinanceOverviewView(kpis, pending, providerMix, openDisputes);
  }

  /**
   * Integer percentage change of {@code current} over {@code prior}; {@code 0} when prior is zero. A
   * degenerate near-zero prior against a large current can produce an astronomically large percentage,
   * so the result is clamped to {@code ±100_000%} rather than risking an {@code int} overflow (a
   * dashboard delta beyond that range is meaningless anyway).
   */
  private static int percentDelta(long current, long prior) {
    if (prior <= 0) {
      return 0;
    }
    BigDecimal pct =
        BigDecimal.valueOf(current - prior)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(prior), 0, java.math.RoundingMode.HALF_UP);
    BigDecimal cap = BigDecimal.valueOf(100_000);
    return pct.max(cap.negate()).min(cap).intValue();
  }

  private static DisputeSummary disputeSummary(Dispute d) {
    return new DisputeSummary(
        d.getId().value(),
        d.getKind(),
        d.getSubject(),
        d.getDetail(),
        d.getAmount().toCedis(),
        d.getOpenedAt() != null ? d.getOpenedAt().toString() : null);
  }

  /** Minor units → bare decimal cedis (reuses the canonical INV-11 conversion). */
  private static BigDecimal cedis(long minor) {
    return MoneyView.ofMinor(minor).amount();
  }

  /** Bare decimal cedis → minor units (for re-summing payout amounts already in cedis). */
  private static long toMinor(BigDecimal cedis) {
    return cedis.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
  }
}
