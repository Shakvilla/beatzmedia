package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonateUser;
import org.shakvilla.beatzmedia.admin.application.port.in.ImpersonationTokenView;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-02.5 (impersonate user). Auth: super-admin ONLY (enforced by
 * the inbound {@code @RolesAllowed}). Heavily audited: the {@link AuditEntry} action text records
 * the target and the token's expiry instant — NEVER the token itself (admin ADD §9). Appends
 * exactly one AuditEntry (INV-10) atomically in the same transaction as token issuance.
 */
@ApplicationScoped
public class ImpersonateUserService implements ImpersonateUser {

  private final AccountAdminPort accountAdminPort;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ImpersonateUserService(
      AccountAdminPort accountAdminPort, AuditWriter auditWriter, IdGenerator idGenerator,
      Clock clock) {
    this.accountAdminPort = accountAdminPort;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ImpersonationTokenView impersonate(String actorId, String targetId) {
    AccountAdminPort.ImpersonationResult result = accountAdminPort.issueImpersonationToken(targetId);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Impersonated user (expires " + result.expiresAt() + ")",
        "Account",
        targetId,
        AuditType.USER,
        null,
        clock.now()));

    return new ImpersonationTokenView(result.token(), result.expiresAt(), result.scopes());
  }
}
