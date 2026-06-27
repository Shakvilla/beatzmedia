package org.shakvilla.beatzmedia.identity.application.service;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.identity.application.port.in.RemoveAdmin;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository;
import org.shakvilla.beatzmedia.identity.application.port.out.AccountRepository.AdminMemberProjection;
import org.shakvilla.beatzmedia.identity.domain.AccountId;
import org.shakvilla.beatzmedia.identity.domain.AdminRole;
import org.shakvilla.beatzmedia.identity.domain.LastSuperAdminException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;
import org.shakvilla.beatzmedia.platform.domain.NotFoundException;
import org.shakvilla.beatzmedia.platform.domain.UnauthorizedException;

/**
 * Application service for LLFR-IDENTITY-03.3 (remove admin). Auth: super-admin only (RBAC
 * re-checked here — DoD §5). Guards LAST_SUPER_ADMIN. Appends one AuditEntry (INV-10). Identity
 * ADD §4.1 / §10.
 */
@ApplicationScoped
public class RemoveAdminService implements RemoveAdmin {

  private final AccountRepository accountRepository;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public RemoveAdminService(
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
  public void remove(AccountId actor, String adminMemberId) {
    // RBAC re-check in application layer (DoD §5)
    assertSuperAdmin(actor);

    Instant now = clock.now();

    AdminMemberProjection projection = accountRepository.findAdminMember(adminMemberId)
        .orElseThrow(() -> new NotFoundException("Admin member not found: " + adminMemberId));

    // Last-super-admin guard: removing the last super-admin is rejected.
    // KNOWN LIMITATION (TOCTOU, accepted for v1 — code-review + security-review sign-off on PR #27):
    // this count-then-act is not atomic under READ COMMITTED, so two concurrent removals/demotions
    // of distinct super-admins could each observe count=2 and both commit, leaving zero super-admins.
    // Hardening follow-up (see backlog WU-IDN-4 note): lock the super-admin rows
    // (SELECT ... WHERE role='super-admin' FOR UPDATE) in the same transaction before counting.
    if (projection.role() == AdminRole.SUPER_ADMIN) {
      long superAdminCount = accountRepository.countAdminsWithRole(AdminRole.SUPER_ADMIN);
      if (superAdminCount <= 1) {
        throw new LastSuperAdminException();
      }
    }

    accountRepository.deleteAdminMember(adminMemberId);

    // Audit entry (INV-10)
    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actor.value(),
        "Removed admin",
        "AdminMember",
        adminMemberId,
        AuditType.SETTINGS,
        null,
        now));
  }

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
