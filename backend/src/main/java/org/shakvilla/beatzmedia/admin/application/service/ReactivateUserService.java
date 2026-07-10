package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.AdminUserRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.ReactivateUser;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-02.4 (reactivate user). Auth: super-admin, moderator
 * (enforced by the inbound {@code @RolesAllowed}). Appends exactly one {@link AuditEntry}
 * (INV-10) atomically in the same transaction as the domain mutation.
 */
@ApplicationScoped
public class ReactivateUserService implements ReactivateUser {

  private final AccountAdminPort accountAdminPort;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ReactivateUserService(
      AccountAdminPort accountAdminPort, AuditWriter auditWriter, IdGenerator idGenerator,
      Clock clock) {
    this.accountAdminPort = accountAdminPort;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AdminUserRowView reactivate(String actorId, String targetId) {
    AccountAdminPort.AccountMutationResult result = accountAdminPort.reactivate(targetId);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Reactivated user",
        "Account",
        targetId,
        AuditType.USER,
        null,
        clock.now()));

    return AdminUserMapper.toView(result);
  }
}
