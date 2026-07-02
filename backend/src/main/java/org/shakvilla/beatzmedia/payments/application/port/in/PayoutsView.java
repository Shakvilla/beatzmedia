package org.shakvilla.beatzmedia.payments.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model for the studio payouts screen. Field names & shape match the frontend {@code Payouts}
 * interface verbatim ({@code Frontend/src/lib/studio-payouts.ts}): {@code available, pending,
 * thisMonth, thisMonthDelta, lifetime, since, earnings[], bySource{sales,royalties,tips}, methods[],
 * transactions[]}. Amounts are decimal cedis (the frontend uses plain numbers; INV-11 conversion
 * happens here at the read boundary).
 *
 * <p>At WU-PAY-3 scope: {@code methods} is empty (WU-PAY-4 owns payout methods) and {@code
 * bySource.royalties} is always {@code 0} (OQ-4: no royalty model, ADR-20).
 */
public record PayoutsView(
    BigDecimal available,
    BigDecimal pending,
    BigDecimal thisMonth,
    int thisMonthDelta,
    BigDecimal lifetime,
    String since,
    List<Earning> earnings,
    BySource bySource,
    List<PayoutMethodView> methods,
    List<PayoutTxnView> transactions) {

  /** A point on the monthly earnings chart: {@code { label, value }}. */
  public record Earning(String label, BigDecimal value) {}

  /** This period's revenue split by source: {@code { sales, royalties, tips }}. */
  public record BySource(BigDecimal sales, BigDecimal royalties, BigDecimal tips) {}

  /**
   * A payout method ({@code { id, label, detail, kind, isDefault }}). WU-PAY-4 populates these; at
   * WU-PAY-3 the methods list is empty.
   */
  public record PayoutMethodView(
      String id, String label, String detail, String kind, boolean isDefault) {}

  /**
   * A ledger transaction row for the creator's payout screen: {@code { id, date, source, type, gross,
   * net, status }}. {@code type} is one of {@code Sale|Royalty|Tip|Cash-out}; {@code gross} is null for
   * cash-outs; {@code net} is the creator's take-home change.
   */
  public record PayoutTxnView(
      String id,
      String date,
      String source,
      String type,
      BigDecimal gross,
      BigDecimal net,
      String status) {}
}
