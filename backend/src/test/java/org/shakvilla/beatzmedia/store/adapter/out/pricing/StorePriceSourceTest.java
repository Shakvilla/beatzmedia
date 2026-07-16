package org.shakvilla.beatzmedia.store.adapter.out.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.commerce.application.port.out.PricedItem;
import org.shakvilla.beatzmedia.commerce.domain.PriceUnavailableException;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.platform.domain.Money;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.LicenseOption;
import org.shakvilla.beatzmedia.store.domain.LicenseTier;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.fakes.FakeStoreRepository;

/** Unit tests for the WU-COM-4 store {@code ModulePriceSource}. */
class StorePriceSourceTest {

  private static final Instant CREATED = Instant.parse("2026-05-18T00:00:00Z");

  private static StoreItem merch() {
    return new StoreItem(
        new StoreItemId("tee-1"), StoreItemType.MERCH, "Tour Tee", "Black Sherif", null, "img.png",
        5000L, Currency.GHS, null, List.of(), "desc", 50, CREATED, List.of(), List.of(), null, null,
        20);
  }

  private static StoreItem beat() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Lease", 10000L, List.of("MP3"), "terms"),
            new LicenseOption(LicenseTier.PREMIUM, "Premium", 30000L, List.of("WAV"), "terms"),
            new LicenseOption(LicenseTier.EXCLUSIVE, "Exclusive", 100000L, List.of("STEMS"), "terms"));
    return new StoreItem(
        new StoreItemId("beat-1"), StoreItemType.BEAT_LICENSE, "Drill Beat", "Joker", null, "img.png",
        10000L, Currency.GHS, Genre.DRILL, List.of("STEMS"), "desc", 92, CREATED, options, List.of(),
        null, null, null);
  }

  private StorePriceSource source() {
    return new StorePriceSource(new FakeStoreRepository().withItem(merch()).withItem(beat()));
  }

  @Test
  void merch_basePrice() {
    PricedItem priced = source().price("tee-1", Map.of());
    assertEquals("Tour Tee", priced.title());
    assertEquals("Black Sherif", priced.subtitle());
    assertEquals(Money.ofMinor(5000, Currency.GHS), priced.unitPrice());
    assertEquals("store", source().entityType());
  }

  @Test
  void merch_noteIsStripped() {
    PricedItem priced = source().price("tee-1:M", Map.of());
    assertEquals(Money.ofMinor(5000, Currency.GHS), priced.unitPrice());
  }

  @Test
  void beatLicense_noTier_usesBasePrice() {
    PricedItem priced = source().price("beat-1", Map.of());
    assertEquals(Money.ofMinor(10000, Currency.GHS), priced.unitPrice());
  }

  @Test
  void beatLicense_selectsTierPriceFromMetadata() {
    PricedItem priced = source().price("beat-1", Map.of("licenseTier", "PREMIUM"));
    assertEquals(Money.ofMinor(30000, Currency.GHS), priced.unitPrice());
  }

  @Test
  void beatLicense_unknownTierIsNotFound() {
    assertThrows(
        PriceUnavailableException.class,
        () -> source().price("beat-1", Map.of("licenseTier", "GOLD")));
  }

  @Test
  void unknownItemIsNotFound() {
    assertThrows(PriceUnavailableException.class, () -> source().price("nope", Map.of()));
  }
}
