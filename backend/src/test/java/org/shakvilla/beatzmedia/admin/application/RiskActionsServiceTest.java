package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort.AccountMutationResult;
import org.shakvilla.beatzmedia.admin.application.service.RiskActionsService;
import org.shakvilla.beatzmedia.admin.domain.IllegalRiskTransitionException;
import org.shakvilla.beatzmedia.admin.domain.RiskLevel;
import org.shakvilla.beatzmedia.admin.domain.RiskSignal;
import org.shakvilla.beatzmedia.admin.domain.RiskSignalNotFoundException;
import org.shakvilla.beatzmedia.admin.domain.RiskStatus;
import org.shakvilla.beatzmedia.admin.fakes.FakeAccountAdminPort;
import org.shakvilla.beatzmedia.admin.fakes.FakeRiskSignalRepository;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link RiskActionsService} (LLFR-ADMIN-07.1) — the review/clear/ban actions, their
 * 404/409 guards, the ban→identity delegation, and INV-10 audit (exactly one entry per action).
 */
@Tag("unit")
class RiskActionsServiceTest {

  private static final String ACTOR = "admin-1";

  private FakeRiskSignalRepository signals;
  private FakeAccountAdminPort accounts;
  private FakeAuditWriter audit;
  private RiskActionsService service;

  @BeforeEach
  void setUp() {
    signals = new FakeRiskSignalRepository();
    accounts = new FakeAccountAdminPort();
    audit = new FakeAuditWriter();
    service =
        new RiskActionsService(
            signals, accounts, audit, FakeIds.sequential("aud"), FakeClock.fixed());
  }

  private RiskSignal openSignal(String id, String subjectRef) {
    return new RiskSignal(
        id, subjectRef, "Payment fraud", "detail", RiskLevel.HIGH, RiskStatus.OPEN,
        Instant.parse("2026-07-12T10:00:00Z"));
  }

  private AccountMutationResult account(String id, String status) {
    return new AccountMutationResult(
        id, "Name", "e@x.com", false, false, status, Instant.now(), Instant.now());
  }

  @Test
  void clearTransitionsAndAuditsExactlyOnce() {
    signals.seed(openSignal("r1", "@artist"));

    var view = service.clear(ACTOR, "r1");

    assertEquals("cleared", view.status());
    assertEquals("cleared", signals.findById("r1").orElseThrow().getStatus().wireValue());
    assertEquals(1, audit.size());
    assertEquals(AuditType.USER, audit.all().get(0).getType());
  }

  @Test
  void reviewAuditsButLeavesStatusOpen() {
    signals.seed(openSignal("r1", "@artist"));

    var view = service.review(ACTOR, "r1");

    assertEquals("open", view.status());
    assertEquals(1, audit.size());
  }

  @Test
  void banFlipsSignalAndSubjectAccountAndAudits() {
    signals.seed(openSignal("r1", "acct-9"));
    accounts.seed(account("acct-9", "active"));

    var view = service.ban(ACTOR, "r1", "KYC mismatch");

    assertEquals("banned", view.status());
    assertEquals("banned", accounts.ban("acct-9").status()); // idempotent; still banned
    assertEquals(1, audit.size());
    assertEquals("KYC mismatch", audit.all().get(0).getReason());
  }

  @Test
  void banOnANonAccountSubjectIs404AndDoesNotAudit() {
    signals.seed(openSignal("r1", "card-4421")); // not a resolvable account

    // The subject is not an account → identity 404s; the @Transactional action rolls back (the
    // rolled-back persisted status is proven by the IT — the shared-reference fake can't model it).
    assertThrows(AccountNotFoundException.class, () -> service.ban(ACTOR, "r1", "reason"));
    assertEquals(0, audit.size());
  }

  @Test
  void actionOnMissingSignalIs404() {
    assertThrows(RiskSignalNotFoundException.class, () -> service.clear(ACTOR, "nope"));
    assertEquals(0, audit.size());
  }

  @Test
  void actionOnAClosedSignalIs409AndDoesNotAudit() {
    RiskSignal s = openSignal("r1", "@artist");
    s.clear();
    signals.seed(s); // already cleared

    assertThrows(IllegalRiskTransitionException.class, () -> service.ban(ACTOR, "r1", "reason"));
    assertTrue(audit.all().isEmpty());
  }
}
