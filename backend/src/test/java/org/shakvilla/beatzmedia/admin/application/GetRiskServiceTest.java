package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.RiskBoardView;
import org.shakvilla.beatzmedia.admin.application.service.GetRiskService;
import org.shakvilla.beatzmedia.admin.domain.RiskLevel;
import org.shakvilla.beatzmedia.admin.domain.RiskSignal;
import org.shakvilla.beatzmedia.admin.domain.RiskStatus;
import org.shakvilla.beatzmedia.admin.fakes.FakeRiskSignalRepository;

/**
 * Unit tests for {@link GetRiskService} (LLFR-ADMIN-07.1): the {@code fraudFlags} KPI is the real
 * open-signal count; the other KPIs are honest-empty; signals map to the frontend shape.
 */
@Tag("unit")
class GetRiskServiceTest {

  private RiskSignal signal(String id, RiskStatus status) {
    return new RiskSignal(
        id, "@subj", "Payment fraud", "detail", RiskLevel.HIGH, status,
        Instant.parse("2026-07-12T10:00:00Z"));
  }

  @Test
  void fraudFlagsIsOpenCountAndOtherKpisAreHonestEmpty() {
    var repo = new FakeRiskSignalRepository();
    repo.seed(signal("r1", RiskStatus.OPEN));
    repo.seed(signal("r2", RiskStatus.OPEN));
    repo.seed(signal("r3", RiskStatus.CLEARED));

    RiskBoardView board = new GetRiskService(repo).board();

    assertEquals(2, board.kpis().fraudFlags()); // r1, r2 open
    assertEquals("0%", board.kpis().chargebackRate());
    assertEquals(0, board.kpis().suspiciousSignups());
    assertEquals("0%", board.kpis().botStreams());
    assertEquals(3, board.signals().size());
    assertEquals("high", board.signals().get(0).level());
  }
}
