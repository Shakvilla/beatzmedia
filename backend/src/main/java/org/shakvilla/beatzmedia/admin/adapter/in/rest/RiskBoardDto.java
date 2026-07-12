package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.RiskBoardView;

/**
 * Wire DTO for {@code GET /v1/admin/risk} — matches the frontend trust &amp; safety screen ({@code
 * RISK_KPIS} + {@code getRiskSignals()}): {@code { kpis: { chargebackRate, suspiciousSignups,
 * fraudFlags, botStreams }, signals: RiskSignal[] }}. Admin ADD §5.1 (LLFR-ADMIN-07.1).
 */
public record RiskBoardDto(KpisDto kpis, List<RiskSignalDto> signals) {

  public record KpisDto(
      String chargebackRate, int suspiciousSignups, int fraudFlags, String botStreams) {}

  public static RiskBoardDto from(RiskBoardView board) {
    RiskBoardView.Kpis k = board.kpis();
    return new RiskBoardDto(
        new KpisDto(k.chargebackRate(), k.suspiciousSignups(), k.fraudFlags(), k.botStreams()),
        board.signals().stream().map(RiskSignalDto::from).toList());
  }
}
