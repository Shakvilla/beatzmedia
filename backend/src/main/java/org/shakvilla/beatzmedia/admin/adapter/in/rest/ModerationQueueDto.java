package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.ModerationQueueView;
import org.shakvilla.beatzmedia.admin.application.port.in.ModerationSummaryView;

/**
 * Response DTO for {@code GET /admin/moderation}: {@code { items, page, size, total, summary } }.
 * Admin ADD §6 (LLFR-ADMIN-04.1).
 */
public record ModerationQueueDto(
    List<ModerationCaseDto> items, int page, int size, long total, SummaryDto summary) {

  public static ModerationQueueDto from(ModerationQueueView view) {
    return new ModerationQueueDto(
        view.items().stream().map(ModerationCaseDto::from).toList(),
        view.page(),
        view.size(),
        view.total(),
        SummaryDto.from(view.summary()));
  }

  /** Matches {@code MOD_SLA_HOURS}/{@code MOD_ESCALATED} semantics in admin-data.ts. */
  public record SummaryDto(long openCount, int slaHours, long escalatedCount) {
    static SummaryDto from(ModerationSummaryView view) {
      return new SummaryDto(view.openCount(), view.slaHours(), view.escalatedCount());
    }
  }
}
