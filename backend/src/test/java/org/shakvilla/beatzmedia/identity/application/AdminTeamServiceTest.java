package org.shakvilla.beatzmedia.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;
import org.shakvilla.beatzmedia.identity.application.port.in.InviteAdmin.InviteAdminCommand;
import org.shakvilla.beatzmedia.identity.application.service.ChangeAdminRoleService;
import org.shakvilla.beatzmedia.identity.application.service.InviteAdminService;
import org.shakvilla.beatzmedia.identity.application.service.ListAdminTeamService;
import org.shakvilla.beatzmedia.identity.application.service.RemoveAdminService;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminMember;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;
import org.shakvilla.beatzmedia.identity.domain.EmailTakenException;
import org.shakvilla.beatzmedia.identity.domain.InvalidRoleException;
import org.shakvilla.beatzmedia.identity.domain.LastSuperAdminException;
import org.shakvilla.beatzmedia.identity.fakes.FakeAccountRepository;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for admin-team use-case services. Covers all WU-IDN-4 acceptance criteria. Uses fakes
 * for all output ports. Testing-strategy §2 / DoD §11.
 */
@Tag("unit")
class AdminTeamServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-26T10:00:00Z");

  private FakeAccountRepository repo;
  private FakeAuditWriter auditWriter;
  private FakeIds ids;
  private FakeClock clock;

  private InviteAdminService inviteService;
  private ChangeAdminRoleService changeRoleService;
  private RemoveAdminService removeService;
  private ListAdminTeamService listService;

  // Seeded super-admin account id
  private static final AccountId SUPER_ADMIN_ID = new AccountId("account-super");
  private static final AccountId FINANCE_ADMIN_ID = new AccountId("account-finance");

  @BeforeEach
  void setUp() {
    repo = new FakeAccountRepository();
    auditWriter = new FakeAuditWriter();
    ids = FakeIds.sequential("test");
    clock = FakeClock.at(NOW);

    inviteService = new InviteAdminService(repo, auditWriter, ids, clock);
    changeRoleService = new ChangeAdminRoleService(repo, auditWriter, ids, clock);
    removeService = new RemoveAdminService(repo, auditWriter, ids, clock);
    listService = new ListAdminTeamService(repo);

    // Seed a super-admin account
    Account superAdminAccount = Account.reconstitute(
        SUPER_ADMIN_ID, "Yaa Mensima", "yaa@beatzclik.com", null,
        false, true, org.shakvilla.beatzmedia.identity.domain.AccountStatus.active,
        NOW, NOW, null);
    repo.seed(superAdminAccount);
    AdminMember superAdmin = new AdminMember("member-super", SUPER_ADMIN_ID, AdminRole.SUPER_ADMIN,
        NOW);
    repo.seedAdminMember(superAdmin);

    // Seed a finance-admin account
    Account financeAdminAccount = Account.reconstitute(
        FINANCE_ADMIN_ID, "Kofi Annor", "kofi@beatzclik.com", null,
        false, true, org.shakvilla.beatzmedia.identity.domain.AccountStatus.active,
        NOW, NOW, null);
    repo.seed(financeAdminAccount);
    AdminMember financeAdmin = new AdminMember("member-finance", FINANCE_ADMIN_ID,
        AdminRole.FINANCE, NOW);
    repo.seedAdminMember(financeAdmin);
  }

  // ---- ListAdminTeam (LLFR-03.1) ----

  @Test
  void list_returns_all_admin_members() {
    List<AdminMemberView> views = listService.list();
    assertEquals(2, views.size());
  }

  @Test
  void list_returns_correct_role_as_kebab_case() {
    List<AdminMemberView> views = listService.list();
    boolean hasSuperAdmin = views.stream().anyMatch(v -> "super-admin".equals(v.role()));
    boolean hasFinance = views.stream().anyMatch(v -> "finance".equals(v.role()));
    org.junit.jupiter.api.Assertions.assertTrue(hasSuperAdmin, "should have super-admin");
    org.junit.jupiter.api.Assertions.assertTrue(hasFinance, "should have finance");
  }

  // ---- InviteAdmin (LLFR-03.2) ----

  @Test
  void invite_creates_admin_member_with_correct_role() {
    AdminMemberView view = inviteService.invite(SUPER_ADMIN_ID,
        new InviteAdminCommand("new@beatzclik.com", AdminRole.MODERATOR));

    assertNotNull(view.id());
    assertEquals("moderator", view.role());
    assertEquals("new@beatzclik.com", view.email());
  }

  @Test
  void invite_appends_exactly_one_audit_entry() {
    inviteService.invite(SUPER_ADMIN_ID,
        new InviteAdminCommand("audit-check@beatzclik.com", AdminRole.EDITOR));
    assertEquals(1, auditWriter.size());
  }

  @Test
  void invite_by_non_super_admin_throws_UnauthorizedException() {
    assertThrows(UnauthorizedException.class, () ->
        inviteService.invite(FINANCE_ADMIN_ID,
            new InviteAdminCommand("attempt@beatzclik.com", AdminRole.SUPPORT)));
  }

  @Test
  void invite_existing_admin_email_throws_EmailTakenException() {
    // kofi@beatzclik.com already has an admin member record
    assertThrows(EmailTakenException.class, () ->
        inviteService.invite(SUPER_ADMIN_ID,
            new InviteAdminCommand("kofi@beatzclik.com", AdminRole.MODERATOR)));
  }

  @Test
  void invite_creates_stub_account_when_email_not_found() {
    long accountsBefore = repo.all().size();
    inviteService.invite(SUPER_ADMIN_ID,
        new InviteAdminCommand("brand-new@example.com", AdminRole.SUPPORT));
    assertEquals(accountsBefore + 1, repo.all().size());
  }

  // ---- ChangeAdminRole (LLFR-03.3) ----

  @Test
  void change_role_updates_member_role() {
    AdminMemberView view = changeRoleService.changeRole(SUPER_ADMIN_ID,
        "member-finance", AdminRole.MODERATOR);
    assertEquals("moderator", view.role());
  }

  @Test
  void change_role_appends_exactly_one_audit_entry() {
    changeRoleService.changeRole(SUPER_ADMIN_ID, "member-finance", AdminRole.EDITOR);
    assertEquals(1, auditWriter.size());
  }

  @Test
  void change_role_by_non_super_admin_throws_UnauthorizedException() {
    assertThrows(UnauthorizedException.class, () ->
        changeRoleService.changeRole(FINANCE_ADMIN_ID, "member-super", AdminRole.FINANCE));
  }

  @Test
  void demoting_last_super_admin_throws_LastSuperAdminException() {
    // Only one super-admin seeded (member-super). Trying to demote it should fail.
    assertThrows(LastSuperAdminException.class, () ->
        changeRoleService.changeRole(SUPER_ADMIN_ID, "member-super", AdminRole.FINANCE));
  }

  @Test
  void change_role_on_missing_member_throws_NotFoundException() {
    assertThrows(NotFoundException.class, () ->
        changeRoleService.changeRole(SUPER_ADMIN_ID, "no-such-id", AdminRole.FINANCE));
  }

  @Test
  void invalid_role_string_throws_InvalidRoleException() {
    assertThrows(InvalidRoleException.class,
        () -> AdminRole.fromWireValue("hacker"));
  }

  @Test
  void null_role_string_throws_InvalidRoleException() {
    assertThrows(InvalidRoleException.class,
        () -> AdminRole.fromWireValue(null));
  }

  // ---- RemoveAdmin (LLFR-03.3) ----

  @Test
  void remove_deletes_admin_member() {
    removeService.remove(SUPER_ADMIN_ID, "member-finance");
    assertEquals(1, repo.allAdminMembers().size()); // only super-admin remains
  }

  @Test
  void remove_appends_exactly_one_audit_entry() {
    removeService.remove(SUPER_ADMIN_ID, "member-finance");
    assertEquals(1, auditWriter.size());
  }

  @Test
  void remove_by_non_super_admin_throws_UnauthorizedException() {
    assertThrows(UnauthorizedException.class, () ->
        removeService.remove(FINANCE_ADMIN_ID, "member-super"));
  }

  @Test
  void removing_last_super_admin_throws_LastSuperAdminException() {
    // Remove finance first so we have only one super-admin
    removeService.remove(SUPER_ADMIN_ID, "member-finance");
    auditWriter.reset();
    // Now try to remove the only super-admin
    assertThrows(LastSuperAdminException.class, () ->
        removeService.remove(SUPER_ADMIN_ID, "member-super"));
  }

  @Test
  void remove_on_missing_member_throws_NotFoundException() {
    assertThrows(NotFoundException.class, () ->
        removeService.remove(SUPER_ADMIN_ID, "does-not-exist"));
  }

  // ---- AdminRole enum ----

  @Test
  void admin_role_wire_values_are_kebab_case() {
    assertEquals("super-admin", AdminRole.SUPER_ADMIN.wireValue());
    assertEquals("finance", AdminRole.FINANCE.wireValue());
    assertEquals("moderator", AdminRole.MODERATOR.wireValue());
    assertEquals("editor", AdminRole.EDITOR.wireValue());
    assertEquals("support", AdminRole.SUPPORT.wireValue());
  }

  @Test
  void admin_role_from_wire_value_parses_all_roles() {
    assertEquals(AdminRole.SUPER_ADMIN, AdminRole.fromWireValue("super-admin"));
    assertEquals(AdminRole.FINANCE, AdminRole.fromWireValue("finance"));
    assertEquals(AdminRole.MODERATOR, AdminRole.fromWireValue("moderator"));
    assertEquals(AdminRole.EDITOR, AdminRole.fromWireValue("editor"));
    assertEquals(AdminRole.SUPPORT, AdminRole.fromWireValue("support"));
  }
}
