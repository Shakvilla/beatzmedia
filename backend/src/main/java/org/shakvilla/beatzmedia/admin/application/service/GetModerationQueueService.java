package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.shakvilla.beatzmedia.admin.application.port.in.GetModerationQueue;
import org.shakvilla.beatzmedia.admin.application.port.in.ModQuery;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationCaseView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationQueueView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationSummaryView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader;
import org.shakvilla.beatzmedia.admin.application.port.out.ModerationCaseRepository;
import org.shakvilla.beatzmedia.admin.domain.ModerationCase;
import org.shakvilla.beatzmedia.platform.domain.Page;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Application service for LLFR-ADMIN-04.1 (moderation queue, read side). Auth: super-admin,
 * moderator, support (read).
 */
@ApplicationScoped
public class GetModerationQueueService implements GetModerationQueue {

  private final ModerationCaseRepository moderationCaseRepository;
  private final CatalogAdminReader catalogAdminReader;

  @Inject
  public GetModerationQueueService(
      ModerationCaseRepository moderationCaseRepository, CatalogAdminReader catalogAdminReader) {
    this.moderationCaseRepository = moderationCaseRepository;
    this.catalogAdminReader = catalogAdminReader;
  }

  @Override
  @Transactional
  public ModerationQueueView queue(ModQuery query, PageRequest page) {
    Page<ModerationCase> result =
        moderationCaseRepository.list(query.status(), query.type(), page);
    List<ModerationCaseView> items = result.items().stream()
        .map(mc -> ModerationCaseMapper.toView(mc, catalogAdminReader))
        .toList();

    ModerationCaseRepository.Summary summary = moderationCaseRepository.summary();
    ModerationSummaryView summaryView = new ModerationSummaryView(
        summary.openCount(), ModerationCase.DEFAULT_SLA_HOURS, summary.escalatedCount());

    return new ModerationQueueView(items, result.page(), result.size(), result.total(), summaryView);
  }
}
