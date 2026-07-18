package org.shakvilla.beatzmedia.catalog.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.shakvilla.beatzmedia.catalog.application.port.in.MoneyView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseDetailView;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.TrackDraftView;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.Track;

/**
 * Stateless mapper from the {@link Release} aggregate (+ its joined {@link Track} rows) to the
 * read views served by {@code StudioReleaseResource}. Shared by every draft-flow application
 * service so the view-building logic lives in exactly one place. Catalog ADD §4.1 / WU-CAT-5.
 */
public final class ReleaseViewMapper {

  private ReleaseViewMapper() {}

  /** {@link StudioReleaseView} — the unchanged list-view subset. */
  public static StudioReleaseView toListView(Release r) {
    return new StudioReleaseView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        dateOf(r),
        r.getTracks().size(),
        0L, // streams not tracked at this level
        MoneyView.ofMinor(0L), // revenue placeholder
        MoneyView.ofMinor(r.getListPriceMinor()));
  }

  /**
   * {@link StudioReleaseDetailView} — the additive superset returned by {@code GET /:id} and
   * every draft-flow mutation. Joins each {@link ReleaseTrack} (position/price/trackId) with its
   * {@link Track} row (title/duration/status), ordered by position. A {@code ReleaseTrack} whose
   * {@code Track} is missing from {@code tracks} falls back to a placeholder (empty title, 0
   * duration, {@code "uploading"} status) rather than failing the mapping.
   */
  public static StudioReleaseDetailView toDetailView(Release r, List<Track> tracks) {
    Map<String, Track> byId =
        tracks.stream().collect(Collectors.toMap(t -> t.getId().value(), t -> t));

    List<TrackDraftView> trackViews = r.getTracks().stream()
        .sorted(Comparator.comparingInt(ReleaseTrack::position))
        .map(rt -> {
          Track t = byId.get(rt.trackId());
          List<TrackDraftView.SplitView> splitViews = r.getSplits().stream()
              .filter(s -> s.trackId().equals(rt.trackId()))
              .map(s -> new TrackDraftView.SplitView(
                  s.id(), s.name(), s.email(), s.role(), s.percent(), s.confirmation().name()))
              .toList();
          return new TrackDraftView(
              rt.trackId(),
              t != null ? t.getTitle() : "",
              t != null ? t.getDurationSec() : 0,
              t != null ? t.getStatus() : "uploading",
              rt.position(),
              MoneyView.ofMinor(rt.priceMinor()),
              splitViews);
        })
        .toList();

    return new StudioReleaseDetailView(
        r.getId(),
        r.getTitle(),
        r.getType(),
        r.getStatus(),
        dateOf(r),
        r.getTracks().size(),
        0L, // streams not tracked at this level
        MoneyView.ofMinor(0L), // revenue placeholder
        MoneyView.ofMinor(r.getListPriceMinor()),
        r.getGenre(),
        r.getDescription(),
        r.getVisibility().toDbValue(),
        r.getScheduledAt() != null ? r.getScheduledAt().toString() : null,
        trackViews);
  }

  private static String dateOf(Release r) {
    return r.getCreatedAt() != null ? r.getCreatedAt().toString() : "—";
  }
}
