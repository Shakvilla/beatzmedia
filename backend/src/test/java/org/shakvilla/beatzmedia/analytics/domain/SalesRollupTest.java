package org.shakvilla.beatzmedia.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/**
 * Unit tests for {@link SalesRollup} fold semantics and the OQ-4 royalty=0 invariant (no royalty
 * accrual model — pure buy-to-own).
 */
@Tag("unit")
class SalesRollupTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final RollupBucket BUCKET = RollupBucket.of(LocalDate.parse("2026-07-01"), Grain.DAILY);

  @Test
  void zero_startsAtAllZeros() {
    SalesRollup rollup = SalesRollup.zero(ARTIST, BUCKET);
    assertEquals(0L, rollup.salesMinor());
    assertEquals(0L, rollup.tipsMinor());
    assertEquals(0L, rollup.royaltyMinor());
    assertEquals(0, rollup.units());
  }

  @Test
  void plusSale_accumulatesGrossAndIncrementsUnits() {
    SalesRollup rollup = SalesRollup.zero(ARTIST, BUCKET).plusSale(1000).plusSale(500);
    assertEquals(1500L, rollup.salesMinor());
    assertEquals(2, rollup.units());
    assertEquals(0L, rollup.tipsMinor(), "plusSale never touches tipsMinor");
  }

  @Test
  void plusTip_accumulatesTipsWithoutTouchingSalesOrUnits() {
    SalesRollup rollup = SalesRollup.zero(ARTIST, BUCKET).plusTip(300).plusTip(200);
    assertEquals(500L, rollup.tipsMinor());
    assertEquals(0L, rollup.salesMinor());
    assertEquals(0, rollup.units(), "a tip is not a unit sale");
  }

  @Test
  void royaltyMinor_isAlwaysZero_evenAfterFolding_OQ4() {
    SalesRollup rollup = SalesRollup.zero(ARTIST, BUCKET).plusSale(1000).plusTip(200);
    assertEquals(0L, rollup.royaltyMinor(), "OQ-4: pure buy-to-own, no royalty accrual");
  }

  @Test
  void constructor_rejectsNonZeroRoyalty_OQ4() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new SalesRollup(ARTIST, BUCKET, 100L, 0L, 1L, 1),
        "a non-zero royaltyMinor must never be constructible (OQ-4)");
  }
}
