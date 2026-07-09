package org.shakvilla.beatzmedia.studio.application.service;

import java.util.List;

import org.shakvilla.beatzmedia.studio.application.port.in.AudienceView;
import org.shakvilla.beatzmedia.studio.application.port.in.Gender;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.Insights;
import org.shakvilla.beatzmedia.studio.application.port.out.AnalyticsReader.MetricSeriesData;
import org.shakvilla.beatzmedia.studio.domain.AudienceRange;

/**
 * Maps {@link Insights} + the requested {@link AudienceRange} to {@link AudienceView} (wire {@code
 * AudienceDto}). Studio ADD §6 / §15 (WU-STU-3).
 *
 * <p>Fields with a real data source ({@code rangeLabel}, {@code followers}, {@code
 * followersGained}, {@code followersPeriod}) are mapped faithfully. {@code monthlyListeners}/{@code
 * listenersDelta}, {@code superfans}, {@code avgSessionSec}/{@code avgSessionDelta}, {@code
 * cities}, {@code gender}, {@code ages}, and {@code superfansList} have no backing rollup dimension
 * (and {@code audience_rollup.unique_listeners}/{@code completion_pct} are staged-but-never-set —
 * always {@code 0} — so a "monthly listeners" figure is not honestly computable either) and are
 * honestly returned empty/zero (never fabricated). See Studio ADD §15 for the full carryover note.
 *
 * <p>{@code followers}/{@code followersGained} both surface the SAME aggregate — the sum of {@code
 * audience_rollup.followers_gained} over the queried window (identical to {@code
 * AnalyticsDto.fans}/{@code metrics.followers.total}) — because {@code analytics} exposes no
 * lifetime/cumulative follower total, only net-new followers gained within a window. This is
 * flagged, not hidden: see Studio ADD §15.
 */
final class AudienceMapper {

  private AudienceMapper() {}

  static AudienceView toView(Insights insights, AudienceRange range) {
    MetricSeriesData followersSeries = insights.metrics().get("followers");
    long followersGained = followersSeries != null ? followersSeries.total() : 0L;

    return new AudienceView(
        insights.rangeLabel(),
        0L, // monthlyListeners — audience_rollup.unique_listeners is always 0 upstream (not computable)
        0, // listenersDelta — depends on monthlyListeners, same gap
        followersGained, // followers — no lifetime total available; see class javadoc
        followersGained, // followersGained — genuinely computed (Σ followers_gained over the window)
        followersPeriodLabel(range),
        0L, // superfans — no per-fan spend/engagement dimension in the rollups
        0L, // avgSessionSec — no session dimension in the rollups
        0, // avgSessionDelta — same gap
        List.of(), // cities — no geo dimension in the rollups
        new Gender(0, 0, 0), // no demographic dimension in the rollups
        List.of(), // ages — no demographic dimension in the rollups
        List.of()); // superfansList — no per-fan dimension in the rollups
  }

  /**
   * A deterministic textual description of the queried range (e.g. {@code "this month"}) —
   * mirrors {@code Frontend/src/lib/studio-analytics.ts}'s {@code AUDIENCE_META} period labels.
   * This is NOT invented data: it describes the range itself, exactly like {@code rangeLabel}/
   * {@code axisLabel} do.
   */
  private static String followersPeriodLabel(AudienceRange range) {
    return switch (range) {
      case SEVEN_DAYS -> "this week";
      case TWENTY_EIGHT_DAYS -> "this month";
      case NINETY_DAYS -> "this quarter";
      case TWELVE_MONTHS -> "this year";
    };
  }
}
