package org.shakvilla.beatzmedia.store.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;

/**
 * Unit tests for {@link StoreItem} construction-time invariants: INV-STORE-A (type/child
 * consistency), INV-STORE-B (BEAT_LICENSE "from" price), INV-STORE-C (stock floor). Store ADD
 * §11.
 */
@Tag("unit")
class StoreItemInvariantsTest {

  private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

  private static StoreItem track() {
    return new StoreItem(
        new StoreItemId("hifi-track"),
        StoreItemType.TRACK,
        "Title",
        "Artist",
        "artist-1",
        "img.png",
        450L,
        Currency.GHS,
        Genre.AFROBEATS,
        List.of("HI-FI LOSSLESS"),
        "desc",
        90,
        NOW,
        List.of(),
        List.of(),
        "Lossless • 24-bit/192kHz",
        null,
        null);
  }

  // ---- INV-STORE-A: licenseOptions iff BEAT_LICENSE ---------------------------------------------

  @Test
  void beatLicense_withoutLicenseOptions_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("beat-1"),
                StoreItemType.BEAT_LICENSE,
                "Beat",
                "Producer",
                null,
                "img.png",
                5000L,
                Currency.GHS,
                Genre.DRILL,
                List.of(),
                null,
                92,
                NOW,
                List.of(),
                List.of(),
                null,
                null,
                null));
  }

  @Test
  void nonBeatLicense_withLicenseOptions_throws() {
    List<LicenseOption> options = List.of(new LicenseOption(LicenseTier.LEASE, "Basic Lease", 5000L, List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("track-1"),
                StoreItemType.TRACK,
                "Track",
                "Artist",
                null,
                "img.png",
                450L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                options,
                List.of(),
                null,
                null,
                null));
  }

  @Test
  void merchVariants_onNonMerchType_throws() {
    List<MerchVariant> variants = List.of(new MerchVariant("Size", List.of("S", "M")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("track-1"),
                StoreItemType.TRACK,
                "Track",
                "Artist",
                null,
                "img.png",
                450L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                List.of(),
                variants,
                null,
                null,
                null));
  }

  @Test
  void quality_onNonTrackAlbumType_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("merch-1"),
                StoreItemType.MERCH,
                "Tee",
                "Artist",
                null,
                "img.png",
                12000L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                List.of(),
                List.of(),
                "Lossless",
                null,
                null));
  }

  @Test
  void dropsAt_onNonExclusiveType_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("merch-1"),
                StoreItemType.MERCH,
                "Tee",
                "Artist",
                null,
                "img.png",
                12000L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                List.of(),
                List.of(),
                null,
                NOW,
                null));
  }

  @Test
  void stockRemaining_onTrackType_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("track-1"),
                StoreItemType.TRACK,
                "Track",
                "Artist",
                null,
                "img.png",
                450L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                List.of(),
                List.of(),
                null,
                null,
                10));
  }

  @Test
  void merch_withStockRemaining_constructsFine() {
    StoreItem item =
        new StoreItem(
            new StoreItemId("merch-1"),
            StoreItemType.MERCH,
            "Tee",
            "Artist",
            null,
            "img.png",
            12000L,
            Currency.GHS,
            null,
            List.of(),
            null,
            90,
            NOW,
            List.of(),
            List.of(new MerchVariant("Size", List.of("S", "M"))),
            null,
            null,
            42);
    assertEquals(42, item.stockRemaining().orElseThrow());
  }

  @Test
  void exclusive_withDropsAtAndStock_constructsFine() {
    StoreItem item =
        new StoreItem(
            new StoreItemId("exclusive-1"),
            StoreItemType.EXCLUSIVE,
            "VIP Meet & Greet",
            "Artist",
            null,
            "img.png",
            80000L,
            Currency.GHS,
            null,
            List.of("LIMITED"),
            null,
            96,
            NOW,
            List.of(),
            List.of(),
            null,
            NOW,
            12);
    assertEquals(NOW, item.dropsAt().orElseThrow());
    assertEquals(12, item.stockRemaining().orElseThrow());
  }

  @Test
  void track_isValid_smokeTest() {
    StoreItem item = track();
    assertEquals(StoreItemType.TRACK, item.type());
  }

  // ---- INV-STORE-B: BEAT_LICENSE base price = lowest tier; LEASE cheapest, EXCLUSIVE dearest -----

  @Test
  void beatLicense_basePriceEqualsLowestTierPrice_constructsFine() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Basic Lease", 5000L, List.of(), null),
            new LicenseOption(LicenseTier.PREMIUM, "Premium Stems", 20000L, List.of(), null),
            new LicenseOption(LicenseTier.EXCLUSIVE, "Exclusive", 100000L, List.of(), null));
    StoreItem item =
        new StoreItem(
            new StoreItemId("beat-1"),
            StoreItemType.BEAT_LICENSE,
            "Beat",
            "Producer",
            null,
            "img.png",
            5000L,
            Currency.GHS,
            Genre.DRILL,
            List.of(),
            null,
            92,
            NOW,
            options,
            List.of(),
            null,
            null,
            null);
    assertEquals(5000L, item.priceMinor());
  }

  @Test
  void beatLicense_basePriceNotLowestTier_throws() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Basic Lease", 5000L, List.of(), null),
            new LicenseOption(LicenseTier.PREMIUM, "Premium Stems", 20000L, List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("beat-1"),
                StoreItemType.BEAT_LICENSE,
                "Beat",
                "Producer",
                null,
                "img.png",
                20000L, // does not equal the lowest tier price (5000)
                Currency.GHS,
                Genre.DRILL,
                List.of(),
                null,
                92,
                NOW,
                options,
                List.of(),
                null,
                null,
                null));
  }

  @Test
  void beatLicense_leaseNotCheapest_throws() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Basic Lease", 20000L, List.of(), null),
            new LicenseOption(LicenseTier.PREMIUM, "Premium Stems", 5000L, List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("beat-1"),
                StoreItemType.BEAT_LICENSE,
                "Beat",
                "Producer",
                null,
                "img.png",
                5000L,
                Currency.GHS,
                Genre.DRILL,
                List.of(),
                null,
                92,
                NOW,
                options,
                List.of(),
                null,
                null,
                null));
  }

  @Test
  void beatLicense_exclusiveNotDearest_throws() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Basic Lease", 5000L, List.of(), null),
            new LicenseOption(LicenseTier.EXCLUSIVE, "Exclusive", 1000L, List.of(), null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("beat-1"),
                StoreItemType.BEAT_LICENSE,
                "Beat",
                "Producer",
                null,
                "img.png",
                1000L,
                Currency.GHS,
                Genre.DRILL,
                List.of(),
                null,
                92,
                NOW,
                options,
                List.of(),
                null,
                null,
                null));
  }

  // ---- INV-STORE-C: stockRemaining >= 0 -----------------------------------------------------------

  @Test
  void negativeStockRemaining_throws() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StoreItem(
                new StoreItemId("merch-1"),
                StoreItemType.MERCH,
                "Tee",
                "Artist",
                null,
                "img.png",
                12000L,
                Currency.GHS,
                null,
                List.of(),
                null,
                90,
                NOW,
                List.of(),
                List.of(),
                null,
                null,
                -1));
  }

  @Test
  void zeroStockRemaining_isValid() {
    StoreItem item =
        new StoreItem(
            new StoreItemId("merch-1"),
            StoreItemType.MERCH,
            "Tee",
            "Artist",
            null,
            "img.png",
            12000L,
            Currency.GHS,
            null,
            List.of(),
            null,
            90,
            NOW,
            List.of(),
            List.of(),
            null,
            null,
            0);
    assertEquals(0, item.stockRemaining().orElseThrow());
  }
}
