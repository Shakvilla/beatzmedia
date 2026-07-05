package org.shakvilla.beatzmedia.analytics.domain;

import java.time.LocalDate;

/**
 * The studio-analytics time window, mirroring {@code Frontend/src/lib/studio-analytics.ts}
 * {@code AnalyticsRange}. Analytics ADD §3.1.
 *
 * <p>Each range selects the {@link Grain} its rollup buckets are read at and the number of buckets
 * (points) that make up the current AND the comparison ("previous") period.
 */
public enum AnalyticsRange {
  SEVEN_DAYS("7d", Grain.DAILY, 7),
  TWENTY_EIGHT_DAYS("28d", Grain.DAILY, 28),
  NINETY_DAYS("90d", Grain.WEEKLY, 13),
  TWELVE_MONTHS("12m", Grain.MONTHLY, 12),
  ALL("all", Grain.MONTHLY, 24);

  private final String wireValue;
  private final Grain grain;
  private final int points;

  AnalyticsRange(String wireValue, Grain grain, int points) {
    this.wireValue = wireValue;
    this.grain = grain;
    this.points = points;
  }

  /** The exact grain rollups must be read at for this range (ADD §3.1 invariant). */
  public Grain grain() {
    return grain;
  }

  /** Number of buckets in the current period (and, symmetrically, the previous period). */
  public int points() {
    return points;
  }

  /** Wire value used by the frontend / query param, e.g. {@code "28d"}. */
  public String wireValue() {
    return wireValue;
  }

  /** Parse a {@code ?range=} query value; defaults to {@link #TWENTY_EIGHT_DAYS} if unrecognised. */
  public static AnalyticsRange fromWire(String value) {
    if (value == null) {
      return TWENTY_EIGHT_DAYS;
    }
    for (AnalyticsRange r : values()) {
      if (r.wireValue.equalsIgnoreCase(value)) {
        return r;
      }
    }
    return TWENTY_EIGHT_DAYS;
  }

  /**
   * The first bucket date of the CURRENT period, given "today" (from the platform {@code Clock}).
   * The current period is the {@code points()} most recent buckets ending at {@code today}
   * (inclusive), at this range's grain.
   */
  public LocalDate currentPeriodStart(LocalDate today) {
    return stepBack(today, points);
  }

  /**
   * The first bucket date of the PREVIOUS (comparison) period — the {@code points()} buckets
   * immediately preceding {@link #currentPeriodStart(LocalDate)}.
   */
  public LocalDate previousPeriodStart(LocalDate today) {
    return stepBack(currentPeriodStart(today), points);
  }

  private LocalDate stepBack(LocalDate from, int steps) {
    return switch (grain) {
      case DAILY -> from.minusDays(steps);
      case WEEKLY -> from.minusWeeks(steps);
      case MONTHLY -> from.minusMonths(steps);
    };
  }
}
