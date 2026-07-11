package org.shakvilla.beatzmedia.admin.adapter.in.rest;

import java.util.List;

import org.shakvilla.beatzmedia.admin.application.port.in.ActionLogEntryView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogItemDetailView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogSplitView;
import org.shakvilla.beatzmedia.admin.application.port.in.CatalogTrackView;

/**
 * Response DTO for {@code GET /admin/catalog/:id} and every catalog-moderation mutation ({@code
 * approve|flag|takedown|reinstate}): {@code { id, title, note, artist, type, status, upc,
 * tracklist, splits, actionLog } }. Admin ADD §6 (LLFR-ADMIN-03.1/.2).
 */
public record CatalogItemDetailDto(
    String id,
    String title,
    String note,
    String artist,
    String type,
    String status,
    String upc,
    List<TrackDto> tracklist,
    List<SplitDto> splits,
    List<ActionLogEntryDto> actionLog) {

  public static CatalogItemDetailDto from(CatalogItemDetailView view) {
    return new CatalogItemDetailDto(
        view.id(),
        view.title(),
        view.note(),
        view.artist(),
        view.type(),
        view.status(),
        view.upc(),
        view.tracklist().stream().map(TrackDto::from).toList(),
        view.splits().stream().map(SplitDto::from).toList(),
        view.actionLog().stream().map(ActionLogEntryDto::from).toList());
  }

  /** {@code { position, trackId, title, isrc, durationSec, price } }. */
  public record TrackDto(
      int position, String trackId, String title, String isrc, int durationSec, long priceMinor) {
    static TrackDto from(CatalogTrackView view) {
      return new TrackDto(
          view.position(), view.trackId(), view.title(), view.isrc(), view.durationSec(),
          view.priceMinor());
    }
  }

  /** {@code { trackId, name, role, percent, confirmation } }. */
  public record SplitDto(String trackId, String name, String role, int percent, String confirmation) {
    static SplitDto from(CatalogSplitView view) {
      return new SplitDto(
          view.trackId(), view.name(), view.role(), view.percent(), view.confirmation());
    }
  }

  /** {@code { id, action, by, time } }. */
  public record ActionLogEntryDto(String id, String action, String by, String time) {
    static ActionLogEntryDto from(ActionLogEntryView view) {
      return new ActionLogEntryDto(view.id(), view.action(), view.by(), view.time().toString());
    }
  }
}
