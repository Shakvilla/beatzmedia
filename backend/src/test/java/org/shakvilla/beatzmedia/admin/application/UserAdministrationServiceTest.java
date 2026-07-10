package org.shakvilla.beatzmedia.admin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.admin.application.port.in.AdminUserRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;
import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.admin.application.port.in.PagedUsersView;
import org.shakvilla.beatzmedia.admin.application.port.in.UserDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.UserQuery;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort.AccountMutationResult;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader.AccountRow;
import org.shakvilla.beatzmedia.admin.application.service.ExportUserDataService;
import org.shakvilla.beatzmedia.admin.application.service.GetUserService;
import org.shakvilla.beatzmedia.admin.application.service.ImpersonateUserService;
import org.shakvilla.beatzmedia.admin.application.service.ListUsersService;
import org.shakvilla.beatzmedia.admin.application.service.ReactivateUserService;
import org.shakvilla.beatzmedia.admin.application.service.SuspendUserService;
import org.shakvilla.beatzmedia.admin.application.service.VerifyUserService;
import org.shakvilla.beatzmedia.admin.domain.UserFilter;
import org.shakvilla.beatzmedia.admin.fakes.FakeAccountAdminPort;
import org.shakvilla.beatzmedia.admin.fakes.FakeIdentityReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditReader;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.identity.domain.AccountAlreadySuspendedException;
import org.shakvilla.beatzmedia.identity.domain.AccountAlreadyVerifiedException;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.identity.domain.AccountNotSuspendedException;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for the WU-ADM-2 user-administration use-case services (LLFR-ADMIN-02.1–.6). Uses
 * fakes for all output ports. One test per acceptance criterion. Testing-strategy §2 / admin ADD
 * §11.
 */
@Tag("unit")
class UserAdministrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-10T10:00:00Z");
  private static final String ACTOR_ID = "account-admin";
  private static final String TARGET_ID = "account-target";

  private FakeIdentityReader identityReader;
  private FakeAccountAdminPort accountAdminPort;
  private FakeAuditWriter auditWriter;
  private FakeAuditReader auditReader;
  private FakeIds ids;
  private FakeClock clock;

  private ListUsersService listUsersService;
  private GetUserService getUserService;
  private VerifyUserService verifyUserService;
  private SuspendUserService suspendUserService;
  private ReactivateUserService reactivateUserService;
  private ImpersonateUserService impersonateUserService;
  private ExportUserDataService exportUserDataService;

  @BeforeEach
  void setUp() {
    identityReader = new FakeIdentityReader();
    accountAdminPort = new FakeAccountAdminPort();
    auditWriter = new FakeAuditWriter();
    auditReader = new FakeAuditReader();
    ids = FakeIds.sequential("audit");
    clock = FakeClock.at(NOW);

    listUsersService = new ListUsersService(identityReader);
    getUserService = new GetUserService(identityReader, auditReader);
    verifyUserService = new VerifyUserService(accountAdminPort, auditWriter, ids, clock);
    suspendUserService = new SuspendUserService(accountAdminPort, auditWriter, ids, clock);
    reactivateUserService = new ReactivateUserService(accountAdminPort, auditWriter, ids, clock);
    impersonateUserService = new ImpersonateUserService(accountAdminPort, auditWriter, ids, clock);
    exportUserDataService = new ExportUserDataService(identityReader, auditWriter, ids, clock);

    identityReader.seedAccount(new AccountRow(
        TARGET_ID, "Ama Boateng", "ama@example.com", false, false, "active", NOW, NOW));
    accountAdminPort.seed(new AccountMutationResult(
        TARGET_ID, "Ama Boateng", "ama@example.com", false, false, "active", NOW, NOW));
  }

  // ---- ListUsers (LLFR-ADMIN-02.1) ----

  @Test
  void list_returns_items_and_real_whole_table_counts() {
    identityReader.seedAccount(new AccountRow(
        "account-artist", "Black Sherif", "team@blacksherif.com", true, true, "active", NOW, NOW));
    identityReader.seedAccount(new AccountRow(
        "account-suspended", "Yaw Mensah", "yaw@example.com", false, false, "suspended", NOW, NOW));

    PagedUsersView result = listUsersService.list(new UserQuery(null, null), PageRequest.defaults());

    assertEquals(3, result.items().size());
    assertEquals(3, result.counts().all());
    assertEquals(2, result.counts().fans());
    assertEquals(1, result.counts().artists());
    assertEquals(1, result.counts().verified());
    assertEquals(1, result.counts().suspended());
  }

  @Test
  void list_filters_by_artists() {
    identityReader.seedAccount(new AccountRow(
        "account-artist", "Black Sherif", "team@blacksherif.com", true, true, "active", NOW, NOW));

    PagedUsersView result =
        listUsersService.list(new UserQuery(null, UserFilter.ARTISTS), PageRequest.defaults());

    assertEquals(1, result.items().size());
    assertEquals("account-artist", result.items().get(0).id());
    // Counts stay whole-table regardless of the applied filter.
    assertEquals(2, result.counts().all());
  }

  // ---- GetUser (LLFR-ADMIN-02.1) ----

  @Test
  void get_returns_summary_and_action_log_for_this_account_only() {
    auditReader.seed(new AuditEntry(
        "e1", ACTOR_ID, "Admin", "Suspended user", "Account", TARGET_ID, AuditType.USER,
        "spam", NOW));
    auditReader.seed(new AuditEntry(
        "e2", ACTOR_ID, "Admin", "Suspended user", "Account", "other-account", AuditType.USER,
        "spam", NOW));

    UserDetailView view = getUserService.get(TARGET_ID);

    assertEquals(TARGET_ID, view.summary().id());
    assertTrue(view.activity().isEmpty(), "activity is an honest empty array (Category B)");
    assertTrue(view.orders().isEmpty(), "orders is an honest empty array (Category B)");
    assertTrue(view.devices().isEmpty(), "devices is an honest empty array (Category B)");
    assertEquals(1, view.actionLog().size(), "only entries targeting THIS account id");
    assertEquals("Suspended user", view.actionLog().get(0).action());
  }

  @Test
  void get_unknown_user_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class, () -> getUserService.get("no-such-account"));
  }

  // ---- VerifyUser (LLFR-ADMIN-02.2) ----

  @Test
  void verify_marks_verified_and_appends_exactly_one_audit_entry() {
    AdminUserRowView view = verifyUserService.verify(ACTOR_ID, TARGET_ID);

    assertTrue(view.verified());
    assertEquals(1, auditWriter.size());
    AuditEntry audit = auditWriter.all().get(0);
    assertEquals(ACTOR_ID, audit.getActor());
    assertEquals(TARGET_ID, audit.getTargetId());
    assertEquals(AuditType.USER, audit.getType());
  }

  @Test
  void verify_already_verified_throws_409_and_appends_no_audit() {
    accountAdminPort.seed(new AccountMutationResult(
        TARGET_ID, "Ama Boateng", "ama@example.com", false, true, "active", NOW, NOW));

    assertThrows(AccountAlreadyVerifiedException.class,
        () -> verifyUserService.verify(ACTOR_ID, TARGET_ID));
    assertEquals(0, auditWriter.size(), "no audit row for a rejected mutation");
  }

  // ---- SuspendUser (LLFR-ADMIN-02.3) ----

  @Test
  void suspend_sets_status_and_appends_audit_entry_with_reason() {
    AdminUserRowView view = suspendUserService.suspend(ACTOR_ID, TARGET_ID, "Spam reports");

    assertEquals("suspended", view.status());
    assertEquals(1, auditWriter.size());
    assertEquals("Spam reports", auditWriter.all().get(0).getReason());
  }

  @Test
  void suspend_already_suspended_throws_409_and_appends_no_audit() {
    accountAdminPort.seed(new AccountMutationResult(
        TARGET_ID, "Ama Boateng", "ama@example.com", false, false, "suspended", NOW, NOW));

    assertThrows(AccountAlreadySuspendedException.class,
        () -> suspendUserService.suspend(ACTOR_ID, TARGET_ID, "reason"));
    assertEquals(0, auditWriter.size());
  }

  // ---- ReactivateUser (LLFR-ADMIN-02.4) ----

  @Test
  void reactivate_sets_status_active_and_appends_audit_entry() {
    accountAdminPort.seed(new AccountMutationResult(
        TARGET_ID, "Ama Boateng", "ama@example.com", false, false, "suspended", NOW, NOW));

    AdminUserRowView view = reactivateUserService.reactivate(ACTOR_ID, TARGET_ID);

    assertEquals("active", view.status());
    assertEquals(1, auditWriter.size());
  }

  @Test
  void reactivate_not_suspended_throws_409_and_appends_no_audit() {
    assertThrows(AccountNotSuspendedException.class,
        () -> reactivateUserService.reactivate(ACTOR_ID, TARGET_ID));
    assertEquals(0, auditWriter.size());
  }

  // ---- ImpersonateUser (LLFR-ADMIN-02.5) ----

  @Test
  void impersonate_returns_token_and_audits_actor_target_and_expiry_never_the_token() {
    Instant expiry = NOW.plusSeconds(900);
    accountAdminPort.seedImpersonation("secret-jwt-value", expiry, java.util.Set.of("fan"));

    ImpersonationTokenView view = impersonateUserService.impersonate(ACTOR_ID, TARGET_ID);

    assertEquals("secret-jwt-value", view.token());
    assertEquals(expiry, view.expiresAt());
    assertEquals(1, auditWriter.size());
    AuditEntry audit = auditWriter.all().get(0);
    assertEquals(ACTOR_ID, audit.getActor());
    assertEquals(TARGET_ID, audit.getTargetId());
    assertTrue(audit.getAction().contains(expiry.toString()), "audit records the token expiry");
    assertTrue(!audit.getAction().contains("secret-jwt-value"), "audit NEVER records the token itself");
  }

  @Test
  void impersonate_unknown_user_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class,
        () -> impersonateUserService.impersonate(ACTOR_ID, "no-such-account"));
  }

  // ---- ExportUserData (LLFR-ADMIN-02.6) ----

  @Test
  void export_returns_queued_job_and_appends_audit_entry() {
    DataExportJobRefView view = exportUserDataService.export(ACTOR_ID, TARGET_ID);

    assertEquals("queued", view.status());
    assertTrue(view.jobId() != null && !view.jobId().isBlank());
    assertEquals(1, auditWriter.size());
  }

  @Test
  void export_unknown_user_throws_AccountNotFoundException() {
    assertThrows(AccountNotFoundException.class,
        () -> exportUserDataService.export(ACTOR_ID, "no-such-account"));
  }
}
