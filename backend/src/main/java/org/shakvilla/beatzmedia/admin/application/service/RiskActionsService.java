package org.shakvilla.beatzmedia.admin.application.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.RiskActions;
import org.shakvilla.beatzmedia.admin.application.port.in.RiskSignalView;
import org.shakvilla.beatzmedia.admin.application.port.out.AccountAdminPort;
import org.shakvilla.beatzmedia.admin.application.port.out.RiskSignalRepository;
import org.shakvilla.beatzmedia.admin.domain.RiskSignal;
import org.shakvilla.beatzmedia.admin.domain.RiskSignalNotFoundException;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-07.1's three risk actions. Auth: moderator / super-admin
 * (inbound {@code @RolesAllowed}). Each action loads the signal, applies the guarded domain
 * transition (409 {@code ILLEGAL_TRANSITION} on a non-{@code open} signal, thrown BEFORE any
 * persistence/audit/identity write), and appends exactly one {@code AuditEntry} (INV-10).
 *
 * <p><strong>Ban.</strong> {@code ban} additionally delegates to {@code identity} (via {@link
 * AccountAdminPort#ban}) to set the subject account {@code banned} + expire its ability to obtain new
 * tokens (stateless JWT, OQ-3). The signal's {@code subjectRef} is treated as the target account ref;
 * a non-account subject 404s at the identity boundary and rolls back the whole action (atomic).
 *
 * <p><strong>Audit type.</strong> There is no dedicated risk/trust {@code AuditType} (the enum is
 * {@code user|catalog|finance|moderation|settings|editorial}); risk actions are audited as {@code
 * USER} since their subject is an account (documented in the ADD). Target is the risk signal id.
 */
@ApplicationScoped
public class RiskActionsService implements RiskActions {

  private final RiskSignalRepository riskSignals;
  private final AccountAdminPort accountAdminPort;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public RiskActionsService(
      RiskSignalRepository riskSignals,
      AccountAdminPort accountAdminPort,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.riskSignals = riskSignals;
    this.accountAdminPort = accountAdminPort;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public RiskSignalView review(String actorId, String signalId) {
    RiskSignal signal = load(signalId);
    signal.review(); // 409 before any write if not open
    riskSignals.save(signal);
    audit(actorId, "Reviewed risk signal", signalId, null);
    return RiskSignalView.of(signal);
  }

  @Override
  @Transactional
  public RiskSignalView clear(String actorId, String signalId) {
    RiskSignal signal = load(signalId);
    signal.clear(); // 409 before any write if not open
    riskSignals.save(signal);
    audit(actorId, "Cleared risk signal", signalId, null);
    return RiskSignalView.of(signal);
  }

  @Override
  @Transactional
  public RiskSignalView ban(String actorId, String signalId, String reason) {
    RiskSignal signal = load(signalId);
    signal.ban(); // 409 before any write/identity call if not open
    // Ban the subject account first: a non-account subject 404s here and rolls back the whole action.
    accountAdminPort.ban(signal.getSubjectRef());
    riskSignals.save(signal);
    audit(actorId, "Banned account", signalId, reason);
    return RiskSignalView.of(signal);
  }

  private RiskSignal load(String signalId) {
    return riskSignals.findById(signalId).orElseThrow(() -> new RiskSignalNotFoundException(signalId));
  }

  private void audit(String actorId, String action, String signalId, String reason) {
    auditWriter.append(
        new AuditEntry(
            idGenerator.newId(),
            actorId,
            action,
            "RiskSignal",
            signalId,
            AuditType.USER,
            reason,
            clock.now()));
  }
}
