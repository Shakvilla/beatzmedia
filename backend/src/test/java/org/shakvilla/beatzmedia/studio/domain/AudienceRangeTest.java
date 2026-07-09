package org.shakvilla.beatzmedia.studio.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AudienceRange} — range parsing (LLFR-STUDIO-03.2, 422 INVALID_RANGE). */
@Tag("unit")
class AudienceRangeTest {

  @Test
  void fromWire_allFourValidValues_parseCorrectly() {
    assertEquals(AudienceRange.SEVEN_DAYS, AudienceRange.fromWire("7d"));
    assertEquals(AudienceRange.TWENTY_EIGHT_DAYS, AudienceRange.fromWire("28d"));
    assertEquals(AudienceRange.NINETY_DAYS, AudienceRange.fromWire("90d"));
    assertEquals(AudienceRange.TWELVE_MONTHS, AudienceRange.fromWire("12m"));
  }

  @Test
  void fromWire_nullOrBlank_defaultsTo28d() {
    assertEquals(AudienceRange.TWENTY_EIGHT_DAYS, AudienceRange.fromWire(null));
    assertEquals(AudienceRange.TWENTY_EIGHT_DAYS, AudienceRange.fromWire(""));
  }

  @Test
  void fromWire_all_isInvalid_narrowerThanAnalyticsRange() {
    // "all" is a valid AnalyticsRange value but NOT a valid AudienceRange — the audience endpoint's
    // contract only lists 7d|28d|90d|12m (Studio ADD §3 / §5.1).
    InvalidRangeException ex = assertThrows(InvalidRangeException.class, () -> AudienceRange.fromWire("all"));
    assertEquals("range", ex.getField());
  }

  @Test
  void fromWire_unrecognizedValue_throwsInvalidRangeException() {
    assertThrows(InvalidRangeException.class, () -> AudienceRange.fromWire("3y"));
  }

  @Test
  void toAnalyticsRange_mapsToEquivalentAnalyticsRange() {
    assertEquals(AnalyticsRange.SEVEN_DAYS, AudienceRange.SEVEN_DAYS.toAnalyticsRange());
    assertEquals(AnalyticsRange.TWENTY_EIGHT_DAYS, AudienceRange.TWENTY_EIGHT_DAYS.toAnalyticsRange());
    assertEquals(AnalyticsRange.NINETY_DAYS, AudienceRange.NINETY_DAYS.toAnalyticsRange());
    assertEquals(AnalyticsRange.TWELVE_MONTHS, AudienceRange.TWELVE_MONTHS.toAnalyticsRange());
  }
}
