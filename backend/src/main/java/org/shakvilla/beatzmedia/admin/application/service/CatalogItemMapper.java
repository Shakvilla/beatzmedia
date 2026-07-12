package org.shakvilla.beatzmedia.admin.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogCountsView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemRowView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogSplitView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogTrackView;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.CatalogCounts;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.CatalogDetailRow;
import org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader.CatalogRow;
import org.shakvilla.beatzmedia.audit.application.port.out.AuditReader;
import org.shakvilla.beatzmedia.audit.domain.AuditEntry;
import org.shakvilla.beatzmedia.audit.domain.AuditFilter;
import org.shakvilla.beatzmedia.platform.domain.PageRequest;

/**
 * Maps {@link org.shakvilla.beatzmedia.admin.application.port.out.CatalogAdminReader} rows to the
 * application-layer views served by every catalog-moderation input port. Shared by {@code
 * ListCatalogModerationService}/{@code GetCatalogItemService} (reads) and the mutation services
 * (approve/flag/takedown/reinstate all respond with the same {@link CatalogItemDetailView} shape
 * per admin ADD §5.1's REST table). {@code note} and each track's {@code isrc}/{@code upc} are
 * always {@code null} (Category B — see {@link CatalogItemDetailView}'s javadoc).
 */
final class CatalogItemMapper {

  /** Most-recent-N action-log entries shown on the catalog item detail page. */
  private static final int ACTION_LOG_SIZE = 20;

  private CatalogItemMapper() {}

  /** Real, most-recent-{@value #ACTION_LOG_SIZE} {@code audit_entry} rows targeting this release. */
  static List<ActionLogEntryView> fetchActionLog(AuditReader auditReader, String releaseId) {
    List<AuditEntry> entries =
        auditReader.query(AuditFilter.byTargetId(releaseId), new PageRequest(1, ACTION_LOG_SIZE))
            .items();
    return entries.stream().map(CatalogItemMapper::toActionLogEntry).toList();
  }

  private static ActionLogEntryView toActionLogEntry(AuditEntry entry) {
    String by = entry.getActorName() != null ? entry.getActorName() : entry.getActor();
    return new ActionLogEntryView(entry.getId(), entry.getAction(), by, entry.getOccurredAt());
  }

  static CatalogItemRowView toRowView(CatalogRow row) {
    return new CatalogItemRowView(
        row.id(), row.title(), null, row.artistName(), row.type(), row.trackCount(), row.status());
  }

  static CatalogCountsView toCountsView(CatalogCounts counts) {
    return new CatalogCountsView(counts.pending(), counts.published(), counts.takedown());
  }

  static CatalogItemDetailView toDetailView(
      CatalogDetailRow row, List<ActionLogEntryView> actionLog) {
    List<CatalogTrackView> tracks = row.tracks().stream()
        .map(t -> new CatalogTrackView(
            t.position(), t.trackId(), t.title(), null, t.durationSec(), t.priceMinor()))
        .toList();
    List<CatalogSplitView> splits = row.splits().stream()
        .map(s -> new CatalogSplitView(s.trackId(), s.name(), s.role(), s.percent(), s.confirmation()))
        .toList();
    return new CatalogItemDetailView(
        row.id(), row.title(), null, row.artistName(), row.type(), row.status(), null, tracks,
        splits, actionLog);
  }
}
