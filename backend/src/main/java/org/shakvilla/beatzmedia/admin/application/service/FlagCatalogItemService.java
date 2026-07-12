package org.shakvilla.beatzmedia.admin.application.service;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.FlagCatalogItem;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.domain.ModReason;
import org.shakvilla.beatzmedia.admin.domain.ModSeverity;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditWriter;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditType;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseNotFoundException;
import org.shakvilla.beatzmedia.platform.application.port.out.Clock;
import org.shakvilla.beatzmedia.platform.application.port.out.IdGenerator;

/**
 * Application service for LLFR-ADMIN-03.2 (flag). Auth: super-admin, moderator. Unlike approve/
 * takedown, {@code flag} is NOT a catalog FSM transition — it opens a {@link ModerationCase}
 * targeting the release ({@code targetRef = "release:" + releaseId}), tying HLFR-ADMIN-03's flag
 * action to HLFR-ADMIN-04's moderation queue (see {@link FlagCatalogItem}'s javadoc / admin ADD
 * §13 WU-ADM-3 as-built for the full design rationale). This service OWNS INV-10's AuditEntry for
 * this action (admin's own aggregate, not a catalog mutation).
 *
 * <p><strong>Default {@code reason}/{@code severity}.</strong> The {@code flag} request body is
 * {@code { note? }} only — it carries no {@code reason}/{@code severity} (those are properties of
 * {@link ModerationCase}, which none of {@code ModReason}'s five fixed content-violation
 * categories cleanly describe for a catalog-quality flag like "duplicate ISRC" or "metadata
 * mismatch"). This service defaults every catalog-sourced flag to {@code ModReason.COPYRIGHT} /
 * {@code ModSeverity.MED} — the closest fit (most real-world catalog flags are rights-integrity
 * disputes) — and records the caller's {@code note} nowhere else but the action-log entry's
 * message. Documented explicitly as a judgment call; a future WU could extend the request body to
 * accept an explicit reason/severity if product wants finer control.
 */
@ApplicationScoped
public class FlagCatalogItemService implements FlagCatalogItem {

  private static final String TARGET_REF_PREFIX = "release:";

  private final CatalogAdminReader catalogAdminReader;
  private final ModerationCaseRepository moderationCaseRepository;
  private final AuditReader auditReader;
  private final AuditWriter auditWriter;
  private final IdGenerator idGenerator;
  private final Clock clock;

  @Inject
  public FlagCatalogItemService(
      CatalogAdminReader catalogAdminReader,
      ModerationCaseRepository moderationCaseRepository,
      AuditReader auditReader,
      AuditWriter auditWriter,
      IdGenerator idGenerator,
      Clock clock) {
    this.catalogAdminReader = catalogAdminReader;
    this.moderationCaseRepository = moderationCaseRepository;
    this.auditReader = auditReader;
    this.auditWriter = auditWriter;
    this.idGenerator = idGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CatalogItemDetailView flag(String actorId, String releaseId, Optional<String> note) {
    CatalogAdminReader.CatalogDetailRow row =
        catalogAdminReader.detail(releaseId).orElseThrow(() -> new ReleaseNotFoundException(releaseId));

    var now = clock.now();
    ModerationCase moderationCase = ModerationCase.open(
        idGenerator.newId(),
        TARGET_REF_PREFIX + releaseId,
        actorId,
        ModReason.COPYRIGHT,
        ModSeverity.MED,
        now);
    moderationCaseRepository.save(moderationCase);

    auditWriter.append(new AuditEntry(
        idGenerator.newId(),
        actorId,
        "Flagged release",
        "Release",
        releaseId,
        AuditType.MODERATION,
        note.orElse(null),
        now));

    return CatalogItemMapper.toDetailView(
        row, CatalogItemMapper.fetchActionLog(auditReader, releaseId));
  }
}
