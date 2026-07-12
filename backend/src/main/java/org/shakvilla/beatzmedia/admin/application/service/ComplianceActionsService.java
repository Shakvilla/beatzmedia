package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceActions;
import org.shakvilla.beatzmedia.admin.application.port.in.ComplianceRequestView;
import org.shakvilla.beatzmedia.admin.application.port.in.DataExportJobRefView;
import org.shakvilla.beatzmedia.admin.application.port.out.ComplianceRequestRepository;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequest;
import org.shakvilla.beatzmedia.admin.domain.ComplianceRequestNotFoundException;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-09.1's compliance actions. Auth: super-admin (OQ-1), enforced at
 * the inbound resource. Each action loads the request, applies the guarded transition (start/complete
 * throw {@link org.shakvilla.beatzmedia.admin.domain.IllegalComplianceTransitionException} 409 BEFORE
 * any persistence/audit write on an illegal move), persists, and appends exactly one {@code
 * AuditEntry} (INV-10).
 *
 * <p><strong>Audit type.</strong> There is no dedicated compliance {@code AuditType} (the enum is
 * {@code user|catalog|finance|moderation|settings|editorial}); compliance actions are audited as
 * {@code USER} — the subject of a DSAR/compliance request is a data subject/user (documented in the
 * ADD). {@code export} is a Category-B honest stub (mints a job id + audits; no DSAR worker exists,
 * same as WU-ADM-2's user data export); {@code notice} records a DMCA notice without a status change.
 */
@ApplicationScoped
public class ComplianceActionsService implements ComplianceActions {

  private final ComplianceRequestRepository requests;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ComplianceActionsService(
      ComplianceRequestRepository requests,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.requests = requests;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ComplianceRequestView start(String actorId, String requestId) {
    ComplianceRequest request = load(requestId);
    request.start(); // 409 before any write if illegal
    requests.save(request);
    audit(actorId, "Started compliance request", requestId, null);
    return ComplianceRequestView.of(request);
  }

  @Override
  @Transactional
  public ComplianceRequestView complete(String actorId, String requestId) {
    ComplianceRequest request = load(requestId);
    request.complete(); // 409 before any write if illegal
    requests.save(request);
    audit(actorId, "Completed compliance request", requestId, null);
    return ComplianceRequestView.of(request);
  }

  @Override
  @Transactional
  public DataExportJobRefView export(String actorId, String requestId) {
    load(requestId); // 404 if missing; status unchanged
    String jobId = idGenerator.newId();
    audit(actorId, "Enqueued DSAR data export", requestId, jobId);
    return new DataExportJobRefView(jobId, "queued");
  }

  @Override
  @Transactional
  public ComplianceRequestView notice(String actorId, String requestId) {
    ComplianceRequest request = load(requestId); // 404 if missing; status unchanged
    audit(actorId, "Recorded compliance notice", requestId, null);
    return ComplianceRequestView.of(request);
  }

  private ComplianceRequest load(String requestId) {
    return requests
        .findById(requestId)
        .orElseThrow(() -> new ComplianceRequestNotFoundException(requestId));
  }

  private void audit(String actorId, String action, String requestId, String reason) {
    auditWriter.append(
        new AuditEntry(
            idGenerator.newId(),
            actorId,
            action,
            "ComplianceRequest",
            requestId,
            AuditType.USER,
            reason,
            clock.now()));
  }
}
