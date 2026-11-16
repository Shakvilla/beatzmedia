package org.shakvilla.beatzmedia.discovery.domain;

import java.util.Comparator;
import java.util.List;

/** Versioned, deterministic scoring policy for organic discovery collections. */
public final class CollectionRanker {

  public static final String FORMULA_VERSION = "collections-v1";

  private CollectionRanker() {}

  public enum Chart { TOP, TRENDING, EMERGING }

  /** Metrics are deduplicated upstream; unique listeners count accounts, never sessions. */
  public record Metrics(
      String contentKey,
      long qualifiedPlays28d,
      long qualifiedPlays24h,
      long qualifiedPlaysPrevious7d,
      long uniqueListeners28d,
      long uniqueListeners24h,
      long completions28d,
      long saves28d,
      long follows28d,
      long conversions28d,
      long lifetimeListeners,
      long creatorMonthlyListeners,
      int ageDays) {
    public Metrics {
      if (contentKey == null || contentKey.isBlank()) {
        throw new IllegalArgumentException("contentKey is required");
      }
      if (qualifiedPlays28d < 0 || qualifiedPlays24h < 0 || qualifiedPlaysPrevious7d < 0
          || uniqueListeners28d < 0 || uniqueListeners24h < 0 || completions28d < 0
          || saves28d < 0 || follows28d < 0 || conversions28d < 0
          || lifetimeListeners < 0 || creatorMonthlyListeners < 0 || ageDays < 0) {
        throw new IllegalArgumentException("collection metrics cannot be negative");
      }
    }
  }

  /** Thresholds are part of the formula version and can be calibrated with production aggregates. */
  public record Policy(
      long minimumTopListeners,
      long minimumRecentListeners,
      long maximumEmergingLifetimeListeners,
      long maximumEmergingCreatorListeners,
      int maximumEmergingAgeDays) {
    public static Policy initial() {
      return new Policy(5, 3, 500, 2_000, 90);
    }
  }

  public record Ranked(String contentKey, long score, String reasonCode, String formulaVersion) {}

  public static List<Ranked> rank(Chart chart, List<Metrics> candidates, Policy policy) {
    return candidates.stream()
        .filter(m -> eligible(chart, m, policy))
        .map(m -> new Ranked(m.contentKey(), score(chart, m), reason(chart), FORMULA_VERSION))
        .sorted(Comparator.comparingLong(Ranked::score).reversed()
            .thenComparing(Ranked::contentKey))
        .toList();
  }

  private static boolean eligible(Chart chart, Metrics m, Policy policy) {
    if (chart == Chart.TOP) return m.uniqueListeners28d() >= policy.minimumTopListeners();
    if (m.uniqueListeners24h() < policy.minimumRecentListeners()) return false;
    return chart != Chart.EMERGING
        || (m.lifetimeListeners() <= policy.maximumEmergingLifetimeListeners()
            && m.creatorMonthlyListeners() <= policy.maximumEmergingCreatorListeners()
            && m.ageDays() <= policy.maximumEmergingAgeDays());
  }

  private static long score(Chart chart, Metrics m) {
    return switch (chart) {
      case TOP -> 4 * m.uniqueListeners28d() + m.qualifiedPlays28d()
          + 3 * m.completions28d() + 5 * m.saves28d()
          + 5 * m.follows28d() + 8 * m.conversions28d();
      case TRENDING -> {
        long expectedDaily = m.qualifiedPlaysPrevious7d() / 7;
        long acceleration = Math.max(0, m.qualifiedPlays24h() - expectedDaily);
        long freshness = Math.max(0, 30 - m.ageDays());
        yield 8 * acceleration + 5 * m.uniqueListeners24h()
            + 2 * m.saves28d() + freshness;
      }
      case EMERGING -> 6 * m.uniqueListeners24h() + 3 * m.qualifiedPlays24h()
          + 4 * m.completions28d() + 5 * m.saves28d() + 5 * m.follows28d();
    };
  }

  private static String reason(Chart chart) {
    return switch (chart) {
      case TOP -> "sustained-popularity";
      case TRENDING -> "recent-acceleration";
      case EMERGING -> "growing-audience";
    };
  }
}
