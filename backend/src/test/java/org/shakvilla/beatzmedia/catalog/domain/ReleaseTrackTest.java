package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReleaseTrack}'s {@code priceMinor} validation (INV-5/INV-11, WU-CAT-5
 * review fix). No framework; plain JUnit 5.
 */
@Tag("unit")
class ReleaseTrackTest {

  @Test
  void negativePriceMinor_throwsInvalidPrice() {
    assertThrows(InvalidPriceException.class, () -> new ReleaseTrack("t1", 0, -1L));
  }

  @Test
  void priceMinorAboveMax_throwsInvalidPrice() {
    assertThrows(
        InvalidPriceException.class,
        () -> new ReleaseTrack("t1", 0, ReleaseTrack.MAX_PRICE_MINOR + 1));
  }

  @Test
  void zeroPriceMinor_isAccepted() {
    assertDoesNotThrow(() -> new ReleaseTrack("t1", 0, 0L));
  }

  @Test
  void priceMinorAtMax_isAccepted() {
    assertDoesNotThrow(() -> new ReleaseTrack("t1", 0, ReleaseTrack.MAX_PRICE_MINOR));
  }
}
