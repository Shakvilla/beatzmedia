package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;
import org.shakvilla.beatzmedia.admin.application.port.in.ExportUserData;
import org.shakvilla.beatzmedia.admin.application.port.out.IdentityReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.identity.domain.AccountNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-02.6 (data export). Auth: super-admin, support (enforced by
 * the inbound {@code @RolesAllowed}). Category B (honest stub, admin ADD §13 WU-ADM-2 as-built):
 * verifies the target account exists, generates a job id, and audits the action — there is no
 * DSAR job queue/worker infrastructure anywhere in this codebase to actually process the export
 * (same precedent as WU-STU-3/4 and WU-ADM-1's {@code /admin/health}). Appends exactly one {@link
 * AuditEntry} (INV-10).
 */
@ApplicationScoped
public class ExportUserDataService implements ExportUserData {

  private final IdentityReader identityReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ExportUserDataService(
      IdentityReader identityReader, AuditWriter auditWriter, IdGenerator idGenerator,
      Clock clock) {
    this.identityReader = identityReader;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public DataExportJobRefView export(String actorId, String targetId) {
    identityReader.findUser(targetId).orElseThrow(() -> new AccountNotFoundException(targetId));

    String jobId = idGenerator.newId();

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Requested data export",
        "Account",
        targetId,
        AuditType.USER,
        null,
        clock.now()));

    return new DataExportJobRefView(jobId, "queued");
  }
}
