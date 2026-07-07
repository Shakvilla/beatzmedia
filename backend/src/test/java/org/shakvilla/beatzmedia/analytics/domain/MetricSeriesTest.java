package org.shakvilla.beatzmedia.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MetricSeries} math: total is always {@code Σ current} (the consistency
 * invariant, ADD §3.1/§11), delta is an integer percentage change vs the previous total, and 0
 * previous total never divides by zero.
 */
@Tag("unit")
class MetricSeriesTest {

  @Test
  void of_totalIsSumOfCurrent() {
    MetricSeries series = MetricSeries.of(List.of(10L, 20L, 30L), List.of(5L, 10L, 15L));
    assertEquals(60L, series.total(), "total must equal Σ current (consistency invariant)");
  }

  @Test
  void of_deltaIsIntegerPercentageChangeVsPreviousTotal() {
    // current total 120, previous total 100 -> +20%
    MetricSeries series = MetricSeries.of(List.of(40L, 80L), List.of(50L, 50L));
    assertEquals(120L, series.total());
    assertEquals(20, series.delta());
  }

  @Test
  void of_negativeDelta_roundsHalfUp() {
    // current total 90, previous total 100 -> -10%
    MetricSeries series = MetricSeries.of(List.of(90L), List.of(100L));
    assertEquals(-10, series.delta());
  }

  @Test
  void of_previousTotalZero_currentZero_deltaZero_noDivideByZero() {
    MetricSeries series = MetricSeries.of(List.of(0L, 0L), List.of(0L, 0L));
    assertEquals(0L, series.total());
    assertEquals(0, series.delta());
  }

  @Test
  void of_previousTotalZero_currentPositive_deltaIsHundred() {
    MetricSeries series = MetricSeries.of(List.of(50L), List.of(0L));
    assertEquals(100, series.delta());
  }

  @Test
  void zero_allZerosWithGivenPointCount() {
    MetricSeries series = MetricSeries.zero(5);
    assertEquals(0L, series.total());
    assertEquals(0, series.delta());
    assertEquals(5, series.current().size());
    assertEquals(5, series.previous().size());
  }

  @Test
  void current_and_previous_areImmutableCopies() {
    java.util.ArrayList<Long> mutable = new java.util.ArrayList<>(List.of(1L, 2L));
    MetricSeries series = MetricSeries.of(mutable, List.of(1L, 1L));
    mutable.add(999L);
    assertEquals(2, series.current().size(), "series must defensively copy the input list");
  }
}
