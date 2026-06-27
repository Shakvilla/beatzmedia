package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.identity.application.port.in.AdminMemberView;
import org.shakvilla.beatzmedia.identity.application.port.in.InviteAdmin;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository.AdminMemberProjection;
import org.shakvilla.beatzmedia.identity.domain.Account;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminMember;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;
import org.shakvilla.beatzmedia.identity.domain.EmailTakenException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for LLFR-IDENTITY-03.2 (invite admin). Auth: super-admin only (RBAC
 * re-checked here — DoD §5). Appends one AuditEntry after commit (INV-10). Identity ADD §4.1 /
 * §10.
 */
@ApplicationScoped
public class InviteAdminService implements InviteAdmin {

  private final AccountRepository accountRepository;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public InviteAdminService(
      AccountRepository accountRepository,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AdminMemberView invite(AccountId actor, InviteAdminCommand command) {
    // RBAC re-check in application layer (DoD §5): actor must be a super-admin
    assertSuperAdmin(actor);

    Instant now = clock.now();

    // Find or create the account for the invited email
    Account account = accountRepository.findByEmail(command.email())
        .orElseGet(() -> {
          // Create a stub account — no credential, fan role, status=active
          AccountId newId = new AccountId(idGenerator.newId());
          Account stub = Account.createFan(newId, command.email(), command.email(), null, now);
          return accountRepository.save(stub);
        });

    // If the account already has an admin-member record, reject with EMAIL_TAKEN (409)
    boolean alreadyAdmin = accountRepository
        .findAllAdminMembers()
        .stream()
        .anyMatch(p -> p.accountId().equals(account.getId()));
    if (alreadyAdmin) {
      throw new EmailTakenException();
    }

    // Create the AdminMember
    AdminMember member = new AdminMember(
        idGenerator.newId(),
        account.getId(),
        command.role(),
        now);
    AdminMember saved = accountRepository.saveAdminMember(member);

    // Fetch projection for the response (includes name + email)
    AdminMemberProjection projection = accountRepository.findAdminMember(saved.getId())
        .orElseThrow(() -> new IllegalStateException(
            "AdminMember just saved not found: " + saved.getId()));

    // Audit entry (INV-10)
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actor.value(),
        "Invited admin",
        "AdminMember",
        saved.getId(),
        AuditType.SETTINGS,
        null,
        now));

    return AdminTeamMapper.toView(projection);
  }

  /**
   * Application-layer RBAC re-check. Throws UNAUTHORIZED if the actor is not a super-admin.
   * DoD §5 mandates the check in both inbound adapter AND service.
   */
  private void assertSuperAdmin(AccountId actor) {
    boolean isSuperAdmin = accountRepository.findAllAdminMembers()
        .stream()
        .anyMatch(p -> p.accountId().equals(actor)
            && p.role() == AdminRole.SUPER_ADMIN);
    if (!isSuperAdmin) {
      throw new UnauthorizedException("Super-admin role required");
    }
  }
}
