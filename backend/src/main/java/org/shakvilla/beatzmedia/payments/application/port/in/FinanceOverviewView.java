package org.shakvilla.beatzmedia.payments.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model for the admin finance overview ({@code GET /v1/admin/finance?range=}, LLFR-ADMIN-05.1).
 * Matches the {@code Finance} shape in {@code Frontend/src/lib/admin-data.ts}: {@code { kpis,
 * pendingPayouts, providerMix, disputes }}.
 *
 * <p><strong>Money convention.</strong> Unlike the standalone finance endpoints (which use the {@code
 * { amount, currency }} envelope, INV-11), the admin dashboard reads money as <em>bare decimal-cedis
 * numbers</em> — the frontend renders {@code ₵${n.toLocaleString()}} over a plain {@code number}. This
 * mirrors the convention already established for {@code AdminOverviewView} (WU-ADM-1) and {@code
 * StudioDefaultsView#trackPrice} (WU-STU-4). {@code gmvMtd}, {@code platformFee}, {@code payoutsDue},
 * {@code momoFloat}, and each payout/dispute {@code amount} are {@link BigDecimal} cedis; {@code
 * gmvDelta}, {@code feeTakePct}, {@code payoutsArtists}, and provider-mix {@code value} are plain
 * counts/percentages.
 */
public record FinanceOverviewView(
    Kpis kpis,
    List<PendingPayoutSummary> pendingPayouts,
    List<ProviderMixEntry> providerMix,
    List<DisputeSummary> disputes) {

  public FinanceOverviewView {
    pendingPayouts = List.copyOf(pendingPayouts);
    providerMix = List.copyOf(providerMix);
    disputes = List.copyOf(disputes);
  }

  /**
   * Headline KPIs — matches {@code Finance.kpis} in {@code admin-data.ts}. {@code gmvMtd}/{@code
   * platformFee}/{@code payoutsDue}/{@code momoFloat} are cedis; {@code gmvDelta}/{@code feeTakePct}
   * are percentages; {@code payoutsArtists} is a count.
   */
  public record Kpis(
      BigDecimal gmvMtd,
      int gmvDelta,
      BigDecimal platformFee,
      int feeTakePct,
      BigDecimal payoutsDue,
      int payoutsArtists,
      BigDecimal momoFloat) {}

  /**
   * A payable withdrawal preview row — matches {@code PendingPayout} ({@code { id, artist, amount,
   * method, status }}) with {@code status} one of {@code ready | kyc_pending}. {@code amount} is bare
   * cedis here (the overview convention), unlike {@link PendingPayoutView} on the payouts endpoint.
   */
  public record PendingPayoutSummary(
      String id, String artist, BigDecimal amount, String method, String status) {}

  /** Provider volume split — matches {@code ProviderMix} ({@code { name, value }}). */
  public record ProviderMixEntry(String name, int value) {}

  /**
   * An open dispute preview row — matches {@code Dispute} ({@code { id, kind, subject, detail, amount?,
   * opened? }}). {@code amount} is bare cedis; {@code opened} is the ISO-8601 open timestamp (same
   * serialization as {@link DisputeView#opened()}).
   */
  public record DisputeSummary(
      String id, String kind, String subject, String detail, BigDecimal amount, String opened) {}
}
