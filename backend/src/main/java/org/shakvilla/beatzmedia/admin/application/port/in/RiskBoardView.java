package org.shakvilla.beatzmedia.admin.application.port.in;

import java.util.List;

/**
 * Read model for {@code GET /v1/admin/risk} (LLFR-ADMIN-07.1), matching the frontend trust &amp;
 * safety screen ({@code RISK_KPIS} + {@code getRiskSignals()} in {@code
 * Frontend/src/lib/admin-data.ts}): {@code { kpis, signals }}.
 *
 * <p><strong>KPIs — real vs. honest-empty.</strong> {@code fraudFlags} is real (the count of {@code
 * open} risk signals, from this module's own table). {@code chargebackRate}, {@code
 * suspiciousSignups}, and {@code botStreams} have no backing fraud-detection/analytics subsystem, so
 * they are honest-empty ({@code "0%"} / {@code 0} / {@code "0%"}) — the same precedent as WU-ADM-1's
 * health payload. Documented as a carryover in the ADD.
 */
public record RiskBoardView(Kpis kpis, List<RiskSignalView> signals) {

  public RiskBoardView {
    signals = List.copyOf(signals);
  }

  /** Trust &amp; safety KPIs — matches {@code RISK_KPIS} ({@code { chargebackRate, suspiciousSignups, fraudFlags, botStreams }}). */
  public record Kpis(String chargebackRate, int suspiciousSignups, int fraudFlags, String botStreams) {}
}
