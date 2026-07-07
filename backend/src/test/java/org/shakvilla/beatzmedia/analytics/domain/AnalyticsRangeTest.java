package org.shakvilla.beatzmedia.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AnalyticsRange} grain selection (ADD §3.1 invariant: 7d/28d→DAILY,
 * 90d→WEEKLY, 12m/all→MONTHLY) and wire parsing.
 */
@Tag("unit")
class AnalyticsRangeTest {

  @Test
  void sevenDays_and_twentyEightDays_selectDailyGrain() {
    assertEquals(Grain.DAILY, AnalyticsRange.SEVEN_DAYS.grain());
    assertEquals(Grain.DAILY, AnalyticsRange.TWENTY_EIGHT_DAYS.grain());
  }

  @Test
  void ninetyDays_selectsWeeklyGrain() {
    assertEquals(Grain.WEEKLY, AnalyticsRange.NINETY_DAYS.grain());
  }

  @Test
  void twelveMonths_and_all_selectMonthlyGrain() {
    assertEquals(Grain.MONTHLY, AnalyticsRange.TWELVE_MONTHS.grain());
    assertEquals(Grain.MONTHLY, AnalyticsRange.ALL.grain());
  }

  @Test
  void fromWire_parsesKnownValues() {
    assertEquals(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.fromWire("7d"));
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire("28d"));
    assertEquals(AnalyticsRange.NINETY_DAYS, AnalyticsRange.fromWire("90d"));
    assertEquals(AnalyticsRange.TWELVE_MONTHS, AnalyticsRange.fromWire("12m"));
    assertEquals(AnalyticsRange.ALL, AnalyticsRange.fromWire("all"));
  }

  @Test
  void fromWire_unknownOrNull_defaultsToTwentyEightDays() {
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire("bogus"));
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire(null));
  }

  @Test
  void currentPeriodStart_daily_stepsBackByPoints() {
    LocalDate today = LocalDate.parse("2026-07-28");
    assertEquals(LocalDate.parse("2026-06-30"), AnalyticsRange.TWENTY_EIGHT_DAYS.currentPeriodStart(today));
  }

  @Test
  void previousPeriodStart_daily_precedesCurrentByAnotherFullWindow() {
    LocalDate today = LocalDate.parse("2026-07-28");
    LocalDate currentStart = AnalyticsRange.TWENTY_EIGHT_DAYS.currentPeriodStart(today);
    LocalDate previousStart = AnalyticsRange.TWENTY_EIGHT_DAYS.previousPeriodStart(today);
    assertEquals(currentStart.minusDays(28), previousStart);
  }
}
