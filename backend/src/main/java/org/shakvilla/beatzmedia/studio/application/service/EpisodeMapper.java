package org.shakvilla.beatzmedia.studio.application.service;

import java.math.BigDecimal;

import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.studio.application.port.in.EpisodeView;
import org.shakvilla.beatzmedia.studio.domain.Episode;
import org.shakvilla.beatzmedia.studio.domain.EpisodeStatus;

/**
 * Maps {@link Episode} domain ↔ {@link EpisodeView} (wire {@code StudioEpisodeDto}). {@code price}
 * is decimal cedis converted at this adapter boundary only (INV-11). Studio ADD §6 (WU-STU-2).
 */
final class EpisodeMapper {

  private EpisodeMapper() {}

  static EpisodeView toView(Episode e, String showTitle) {
    BigDecimal price = Money.ofMinor(e.priceMinor(), e.currency()).toCedis();
    String publishedAt = displayPublishedAt(e);
    return new EpisodeView(
        e.id().value(),
        e.showId().value(),
        showTitle == null ? "" : showTitle,
        e.title(),
        e.durationSec(),
        e.status().name(),
        e.premium(),
        price,
        publishedAt,
        e.plays());
  }

  /**
   * {@code publishedAt} on the wire doubles as "the date to show in the Studio episode list":
   * the actual publish instant once {@code published}, the anticipated go-live instant while
   * {@code scheduled} (mirrors the {@code studio-data.ts} mock convention), {@code null} while
   * {@code draft}.
   */
  private static String displayPublishedAt(Episode e) {
    if (e.status() == EpisodeStatus.published) {
      return e.publishedAt() != null ? e.publishedAt().toString() : null;
    }
    if (e.status() == EpisodeStatus.scheduled) {
      return e.scheduledAt() != null ? e.scheduledAt().toString() : null;
    }
    return null;
  }
}
