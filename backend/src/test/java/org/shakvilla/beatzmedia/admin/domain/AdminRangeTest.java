package org.shakvilla.beatzmedia.admin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

/**
 * Unit tests for {@link AdminRange#fromWireValue(String)} (LLFR-ADMIN-01.1). Admin ADD §16
 * (WU-ADM-1).
 */
@Tag("unit")
class AdminRangeTest {

  @Test
  void fromWireValue_24h_parsesToTwentyFourHours() {
    assertEquals(AdminRange.TWENTY_FOUR_HOURS, AdminRange.fromWireValue("24h"));
    assertEquals(1, AdminRange.TWENTY_FOUR_HOURS.days());
  }

  @Test
  void fromWireValue_7d_parsesToSevenDays() {
    assertEquals(AdminRange.SEVEN_DAYS, AdminRange.fromWireValue("7d"));
    assertEquals(7, AdminRange.SEVEN_DAYS.days());
  }

  @Test
  void fromWireValue_30d_parsesToThirtyDays() {
    assertEquals(AdminRange.THIRTY_DAYS, AdminRange.fromWireValue("30d"));
    assertEquals(30, AdminRange.THIRTY_DAYS.days());
  }

  @Test
  void fromWireValue_nullOrBlank_defaultsToSevenDays() {
    assertEquals(AdminRange.SEVEN_DAYS, AdminRange.fromWireValue(null));
    assertEquals(AdminRange.SEVEN_DAYS, AdminRange.fromWireValue(""));
    assertEquals(AdminRange.SEVEN_DAYS, AdminRange.fromWireValue("  "));
  }

  @Test
  void fromWireValue_unrecognized_throwsInvalidAdminRangeException_mapsTo422() {
    InvalidAdminRangeException ex =
        assertThrows(InvalidAdminRangeException.class, () -> AdminRange.fromWireValue("bogus"));
    assertEquals(org.shakvilla.beatzmedia.platform.domain.ErrorCode.INVALID_RANGE, ex.getErrorCode());
  }

  @Test
  void wireValues_matchFrontendContract() {
    assertEquals("24h", AdminRange.TWENTY_FOUR_HOURS.wireValue());
    assertEquals("7d", AdminRange.SEVEN_DAYS.wireValue());
    assertEquals("30d", AdminRange.THIRTY_DAYS.wireValue());
  }
}
