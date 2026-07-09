package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AnalyticsRange} — range parsing (LLFR-STUDIO-03.1, 422 INVALID_RANGE). */
@Tag("unit")
class AnalyticsRangeTest {

  @Test
  void fromWire_allFiveValidValues_parseCorrectly() {
    assertEquals(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.fromWire("7d"));
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire("28d"));
    assertEquals(AnalyticsRange.NINETY_DAYS, AnalyticsRange.fromWire("90d"));
    assertEquals(AnalyticsRange.TWELVE_MONTHS, AnalyticsRange.fromWire("12m"));
    assertEquals(AnalyticsRange.ALL, AnalyticsRange.fromWire("all"));
  }

  @Test
  void fromWire_caseInsensitive() {
    assertEquals(AnalyticsRange.ALL, AnalyticsRange.fromWire("ALL"));
  }

  @Test
  void fromWire_nullOrBlank_defaultsTo28d() {
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire(null));
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire(""));
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AnalyticsRange.fromWire("  "));
  }

  @Test
  void fromWire_unrecognizedValue_throwsInvalidRangeException() {
    InvalidRangeException ex = assertThrows(InvalidRangeException.class, () -> AnalyticsRange.fromWire("3y"));
    assertEquals("range", ex.getField());
  }
}
