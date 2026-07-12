package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;
import org.shakvilla.beatzmedia.admin.application.service.ComplianceActionsService;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequestNotFoundException;
import org.shakvilla.beatzmedia.admin.domain.ComplianceStatus;
import org.shakvilla.beatzmedia.admin.domain.ComplianceType;
import org.shakvilla.beatzmedia.admin.domain.IllegalComplianceTransitionException;
import org.shakvilla.beatzmedia.admin.fakes.FakeComplianceRequestRepository;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link ComplianceActionsService} (LLFR-ADMIN-09.1): start/complete transitions +
 * 404/409, the export honest-stub, notice, and INV-10 audit (one entry per action).
 */
@Tag("unit")
class ComplianceActionsServiceTest {

  private static final String ACTOR = "admin-1";

  private FakeComplianceRequestRepository requests;
  private FakeAuditWriter audit;
  private ComplianceActionsService service;

  @BeforeEach
  void setUp() {
    requests = new FakeComplianceRequestRepository();
    audit = new FakeAuditWriter();
    service = new ComplianceActionsService(requests, audit, FakeIds.sequential("id"), FakeClock.fixed());
  }

  private ComplianceRequest seed(String id, ComplianceStatus status) {
    ComplianceRequest r =
        new ComplianceRequest(
            id, ComplianceType.DSAR_EXPORT, "@ama_b", "export", Instant.parse("2026-07-20T00:00:00Z"),
            status, Instant.parse("2026-07-12T10:00:00Z"));
    requests.seed(r);
    return r;
  }

  @Test
  void startTransitionsAndAuditsOnce() {
    seed("co1", ComplianceStatus.NEW);
    var view = service.start(ACTOR, "co1");
    assertEquals("in_progress", view.status());
    assertEquals(1, audit.size());
    assertEquals(AuditType.USER, audit.all().get(0).getType());
  }

  @Test
  void completeTransitionsAndAudits() {
    seed("co1", ComplianceStatus.IN_PROGRESS);
    var view = service.complete(ACTOR, "co1");
    assertEquals("completed", view.status());
    assertEquals(1, audit.size());
  }

  @Test
  void exportReturnsQueuedJobAndAudits() {
    seed("co1", ComplianceStatus.NEW);
    DataExportJobRefView job = service.export(ACTOR, "co1");
    assertEquals("queued", job.status());
    assertEquals(1, audit.size());
    assertEquals("Enqueued DSAR data export", audit.all().get(0).getAction());
  }

  @Test
  void noticeAuditsWithoutStatusChange() {
    seed("co1", ComplianceStatus.NEW);
    var view = service.notice(ACTOR, "co1");
    assertEquals("new", view.status());
    assertEquals(1, audit.size());
  }

  @Test
  void illegalStartIs409AndDoesNotAudit() {
    seed("co1", ComplianceStatus.COMPLETED);
    assertThrows(IllegalComplianceTransitionException.class, () -> service.start(ACTOR, "co1"));
    assertEquals(0, audit.size());
  }

  @Test
  void missingRequestIs404() {
    assertThrows(ComplianceRequestNotFoundException.class, () -> service.complete(ACTOR, "nope"));
    assertEquals(0, audit.size());
  }
}
