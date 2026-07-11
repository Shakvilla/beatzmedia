package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.ModQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationQueueView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.CatalogDetailRow;
import org.shakvilla.beatzmedia.admin.application.service.GetModerationQueueService;
import org.shakvilla.beatzmedia.admin.application.service.ModerationActionsService;
import org.shakvilla.beatzmedia.admin.domain.IllegalModerationTransitionException;
import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModSeverity;
import org.shakvilla.beatzmedia.admin.domain.ModStatus;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.admin.domain.ModerationCaseNotFoundException;
import org.shakvilla.beatzmedia.admin.fakes.FakeCatalogAdminReader;
import org.shakvilla.beatzmedia.admin.fakes.FakeModerationCaseRepository;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the WU-ADM-3 moderation-queue use-case services (LLFR-ADMIN-04.1). Uses fakes
 * for all output ports. Testing-strategy §2 / admin ADD §11.
 */
@Tag("unit")
class ModerationQueueServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");
  private static final String ACTOR_ID = "account-admin";

  private FakeModerationCaseRepository moderationCaseRepository;
  private FakeCatalogAdminReader catalogAdminReader;
  private FakeAuditWriter auditWriter;
  private FakeIds ids;
  private FakeClock clock;

  private GetModerationQueueService getModerationQueueService;
  private ModerationActionsService moderationActionsService;

  @BeforeEach
  void setUp() {
    moderationCaseRepository = new FakeModerationCaseRepository();
    catalogAdminReader = new FakeCatalogAdminReader();
    auditWriter = new FakeAuditWriter();
    ids = FakeIds.sequential("audit");
    clock = FakeClock.at(NOW);

    getModerationQueueService =
        new GetModerationQueueService(moderationCaseRepository, catalogAdminReader);
    moderationActionsService = new ModerationActionsService(
        moderationCaseRepository, catalogAdminReader, auditWriter, ids, clock);

    catalogAdminReader.seed(new CatalogDetailRow(
        "release-1", "Iron Boy", "artist-1", "Black Sherif", "album", "in_review", NOW,
        List.of(), List.of()));
    moderationCaseRepository.save(ModerationCase.open(
        "case-1", "release:release-1", "admin-1", ModReason.COPYRIGHT, ModSeverity.HIGH, NOW));
  }

  // ---- GetModerationQueue (LLFR-ADMIN-04.1) ----

  @Test
  void queue_resolves_item_label_from_release_target_ref() {
    ModerationQueueView view =
        getModerationQueueService.queue(new ModQuery(null, null), PageRequest.defaults());

    assertEquals(1, view.items().size());
    ModerationCaseView item = view.items().get(0);
    assertEquals("Release · \"Iron Boy\" by Black Sherif", item.item());
    assertEquals(1, view.summary().openCount());
    assertEquals(ModerationCase.DEFAULT_SLA_HOURS, view.summary().slaHours());
    assertEquals(0, view.summary().escalatedCount());
  }

  @Test
  void queue_falls_back_to_raw_target_ref_when_release_unresolvable() {
    moderationCaseRepository.save(ModerationCase.open(
        "case-2", "release:no-such-release", "admin-1", ModReason.SPAM, ModSeverity.LOW, NOW));

    ModerationQueueView view =
        getModerationQueueService.queue(new ModQuery(null, null), PageRequest.defaults());

    ModerationCaseView caseTwo = view.items().stream()
        .filter(i -> i.id().equals("case-2")).findFirst().orElseThrow();
    assertEquals("release:no-such-release", caseTwo.item());
  }

  @Test
  void queue_filters_by_status_and_type() {
    moderationCaseRepository.save(ModerationCase.open(
        "case-3", "release:release-1", "admin-1", ModReason.SPAM, ModSeverity.LOW, NOW));

    ModerationQueueView spamOnly = getModerationQueueService.queue(
        new ModQuery(null, ModReason.SPAM), PageRequest.defaults());

    assertEquals(1, spamOnly.items().size());
    assertEquals("case-3", spamOnly.items().get(0).id());
  }

  // ---- ModerationActions (LLFR-ADMIN-04.1) ----

  @Test
  void review_moves_case_to_in_review_and_appends_audit_entry() {
    ModerationCaseView view = moderationActionsService.review(ACTOR_ID, "case-1");

    assertEquals("in_review", view.status());
    assertEquals(1, auditWriter.size());
    AuditEntry audit = auditWriter.all().get(0);
    assertEquals(ACTOR_ID, audit.getActor());
    assertEquals("case-1", audit.getTargetId());
    assertEquals(AuditType.MODERATION, audit.getType());
    assertEquals("Reviewed report", audit.getAction());
  }

  @Test
  void approve_resolves_case_and_appends_audit_entry() {
    ModerationCaseView view = moderationActionsService.approve(ACTOR_ID, "case-1");
    assertEquals("resolved", view.status());
    assertEquals("Approved content", auditWriter.all().get(0).getAction());
  }

  @Test
  void remove_resolves_case_with_optional_reason_recorded_on_audit() {
    ModerationCaseView view =
        moderationActionsService.remove(ACTOR_ID, "case-1", Optional.of("Confirmed copyright"));
    assertEquals("resolved", view.status());
    assertEquals("Removed content", auditWriter.all().get(0).getAction());
    assertEquals("Confirmed copyright", auditWriter.all().get(0).getReason());
  }

  @Test
  void escalate_sets_flag_and_leaves_status_unchanged() {
    ModerationCaseView view = moderationActionsService.escalate(ACTOR_ID, "case-1");
    assertTrue(view.escalated());
    assertEquals("open", view.status());
    assertEquals("Escalated report", auditWriter.all().get(0).getAction());
  }

  @Test
  void dismiss_resolves_case() {
    ModerationCaseView view = moderationActionsService.dismiss(ACTOR_ID, "case-1");
    assertEquals("resolved", view.status());
  }

  @Test
  void action_on_unknown_case_throws_ModerationCaseNotFoundException_and_appends_no_audit() {
    assertThrows(ModerationCaseNotFoundException.class,
        () -> moderationActionsService.review(ACTOR_ID, "no-such-case"));
    assertEquals(0, auditWriter.size());
  }

  @Test
  void action_on_already_resolved_case_throws_409_and_appends_no_audit() {
    moderationActionsService.approve(ACTOR_ID, "case-1");
    auditWriter.reset();

    assertThrows(IllegalModerationTransitionException.class,
        () -> moderationActionsService.dismiss(ACTOR_ID, "case-1"));
    assertEquals(0, auditWriter.size(), "no audit row for a rejected mutation");
  }
}
