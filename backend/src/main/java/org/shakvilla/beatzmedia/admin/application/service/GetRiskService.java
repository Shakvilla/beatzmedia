package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.GetRisk;
import org.shakvilla.beatzmedia.admin.application.port.in.RiskBoardView;
import org.shakvilla.beatzmedia.admin.application.port.in.RiskSignalView;
import org.shakvilla.beatzmedia.admin.application.port.out.RiskSignalRepository;

/**
 * Read service for {@link GetRisk} (LLFR-ADMIN-07.1). Builds the trust &amp; safety board from this
 * module's own {@code risk_signal} table. Read-only; nothing audited. Moderator/super-admin scope is
 * enforced at the inbound resource.
 *
 * <p><strong>KPIs.</strong> {@code fraudFlags} is real — the count of {@code open} signals. {@code
 * chargebackRate}/{@code suspiciousSignups}/{@code botStreams} have no backing fraud-detection or
 * analytics subsystem, so they are honest-empty ({@code "0%"} / {@code 0} / {@code "0%"}), the same
 * precedent as WU-ADM-1's health payload (admin ADD §13). Documented carryover.
 */
@ApplicationScoped
public class GetRiskService implements GetRisk {

  private final RiskSignalRepository riskSignals;

  @Inject
  public GetRiskService(RiskSignalRepository riskSignals) {
    this.riskSignals = riskSignals;
  }

  @Override
  @Transactional
  public RiskBoardView board() {
    var signals = riskSignals.list().stream().map(RiskSignalView::of).toList();
    int fraudFlags = (int) riskSignals.countOpen();
    // Category B (honest-empty): no fraud-detection/analytics subsystem backs these yet.
    var kpis = new RiskBoardView.Kpis("0%", 0, fraudFlags, "0%");
    return new RiskBoardView(kpis, signals);
  }
}
