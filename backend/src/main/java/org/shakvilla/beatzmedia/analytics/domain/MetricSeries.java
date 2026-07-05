package org.shakvilla.beatzmedia.analytics.domain;

import java.util.List;

/**
 * Shape of {@code Frontend/src/lib/studio-analytics.ts} {@code MetricSeries}: {@code total},
 * {@code delta} (integer percentage vs the previous period), and the per-bucket {@code current[]} /
 * {@code previous[]} value arrays. Analytics ADD §3.1.
 *
 * <p><strong>Consistency invariant (ADD §3.1 / §11):</strong> {@code total == Σ current} and
 * {@code total} equals the sum of the underlying rollup values over the window — this is the
 * property {@link #of(List, List)} guarantees by construction (it never independently recomputes
 * the total from anything except {@code current}).
 */
public record MetricSeries(long total, int delta, List<Long> current, List<Long> previous) {

  public MetricSeries {
    current = List.copyOf(current);
    previous = List.copyOf(previous);
  }

  /**
   * Build a series from the raw per-bucket current/previous values. {@code total} is always
   * {@code Σ current} (never independently derived) so the consistency invariant holds by
   * construction. {@code delta} is the integer percentage change of {@code Σ current} vs
   * {@code Σ previous}, 0 when the previous total is 0 (avoids a divide-by-zero / infinite delta).
   */
  public static MetricSeries of(List<Long> current, List<Long> previous) {
    long total = sum(current);
    long previousTotal = sum(previous);
    int delta = percentDelta(total, previousTotal);
    return new MetricSeries(total, delta, current, previous);
  }

  /** An all-zero series with {@code points} buckets in both periods. */
  public static MetricSeries zero(int points) {
    List<Long> zeros = java.util.Collections.nCopies(points, 0L);
    return new MetricSeries(0L, 0, zeros, zeros);
  }

  private static long sum(List<Long> values) {
    long s = 0L;
    for (long v : values) {
      s += v;
    }
    return s;
  }

  /** Integer percentage change from {@code previous} to {@code current}, half-up, 0 if previous==0. */
  static int percentDelta(long current, long previous) {
    if (previous == 0) {
      return current == 0 ? 0 : 100;
    }
    // half-up rounding of ((current - previous) / previous) * 100
    long numerator = (current - previous) * 100L;
    long divisor = previous;
    long sign = (numerator < 0) != (divisor < 0) ? -1 : 1;
    long absNum = Math.abs(numerator);
    long absDiv = Math.abs(divisor);
    long rounded = (absNum * 2 + absDiv) / (absDiv * 2);
    return (int) (sign * rounded);
  }
}
