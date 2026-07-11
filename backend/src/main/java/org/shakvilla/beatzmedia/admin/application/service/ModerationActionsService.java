package org.shakvilla.beatzmedia.admin.application.service;

import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.ModerationActions;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.admin.domain.ModerationCaseNotFoundException;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-04.1's five queue actions. Auth: super-admin, moderator.
 * Each action loads the case, applies the domain transition (which throws {@link
 * org.shakvilla.beatzmedia.admin.domain.IllegalModerationTransitionException}, 409, BEFORE any
 * persistence/audit write on an illegal move), persists, and appends exactly one {@code
 * AuditEntry} (INV-10, {@code AuditType.MODERATION}) — this is admin's own aggregate, so admin
 * owns the audit write.
 */
@ApplicationScoped
public class ModerationActionsService implements ModerationActions {

  private final ModerationCaseRepository moderationCaseRepository;
  private final CatalogAdminReader catalogAdminReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public ModerationActionsService(
      ModerationCaseRepository moderationCaseRepository,
      CatalogAdminReader catalogAdminReader,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.moderationCaseRepository = moderationCaseRepository;
    this.catalogAdminReader = catalogAdminReader;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public ModerationCaseView review(String actorId, String caseId) {
    return apply(actorId, caseId, ModerationCase::review, "Reviewed report", null);
  }

  @Override
  @Transactional
  public ModerationCaseView approve(String actorId, String caseId) {
    return apply(actorId, caseId, ModerationCase::approve, "Approved content", null);
  }

  @Override
  @Transactional
  public ModerationCaseView remove(String actorId, String caseId, Optional<String> reason) {
    return apply(actorId, caseId, ModerationCase::remove, "Removed content", reason.orElse(null));
  }

  @Override
  @Transactional
  public ModerationCaseView escalate(String actorId, String caseId) {
    return apply(actorId, caseId, ModerationCase::escalate, "Escalated report", null);
  }

  @Override
  @Transactional
  public ModerationCaseView dismiss(String actorId, String caseId) {
    return apply(actorId, caseId, ModerationCase::dismiss, "Dismissed report", null);
  }

  private ModerationCaseView apply(
      String actorId, String caseId, Consumer<ModerationCase> transition, String action,
      String reason) {
    ModerationCase moderationCase = moderationCaseRepository.findById(caseId)
        .orElseThrow(() -> new ModerationCaseNotFoundException(caseId));

    transition.accept(moderationCase); // throws before any persistence/audit write if illegal
    moderationCaseRepository.save(moderationCase);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        action,
        "ModerationCase",
        caseId,
        AuditType.MODERATION,
        reason,
        clock.now()));

    return ModerationCaseMapper.toView(moderationCase, catalogAdminReader);
  }
}
