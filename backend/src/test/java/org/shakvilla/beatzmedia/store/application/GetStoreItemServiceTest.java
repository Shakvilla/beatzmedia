package org.shakvilla.beatzmedia.store.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.platform.domain.Currency;
import org.shakvilla.beatzmedia.store.application.port.in.StoreItemView;
import org.shakvilla.beatzmedia.store.application.service.GetStoreItemService;
import org.shakvilla.beatzmedia.store.domain.Genre;
import org.shakvilla.beatzmedia.store.domain.LicenseOption;
import org.shakvilla.beatzmedia.store.domain.LicenseTier;
import org.shakvilla.beatzmedia.store.domain.StoreItem;
import org.shakvilla.beatzmedia.store.domain.StoreItemId;
import org.shakvilla.beatzmedia.store.domain.StoreItemNotFoundException;
import org.shakvilla.beatzmedia.store.domain.StoreItemType;
import org.shakvilla.beatzmedia.store.fakes.FakeStoreRepository;

/** Unit tests for {@link GetStoreItemService} — LLFR-STORE-01.2 (store product detail). */
@Tag("unit")
class GetStoreItemServiceTest {

  @Test
  void get_knownBeatLicense_returnsLicenseOptionTiers() {
    List<LicenseOption> options =
        List.of(
            new LicenseOption(LicenseTier.LEASE, "Basic Lease", 5000L, List.of("Tagged MP3"), "terms"),
            new LicenseOption(LicenseTier.PREMIUM, "Premium Stems", 20000L, List.of("WAV"), "terms"),
            new LicenseOption(LicenseTier.EXCLUSIVE, "Exclusive", 100000L, List.of("Full transfer"), "terms"));
    StoreItem beat =
        new StoreItem(
            new StoreItemId("beat-konongo-drill"),
            StoreItemType.BEAT_LICENSE,
            "Konongo Drill Type Beat",
            "Joker Nharnah",
            null,
            "img.png",
            5000L,
            Currency.GHS,
            Genre.DRILL,
            List.of("STEMS INCLUDED"),
            "desc",
            92,
            Instant.parse("2026-05-18T00:00:00Z"),
            options,
            List.of(),
            null,
            null,
            null);
    FakeStoreRepository repo = new FakeStoreRepository().withItem(beat);
    GetStoreItemService service = new GetStoreItemService(repo);

    StoreItemView view = service.get(new StoreItemId("beat-konongo-drill"));

    assertEquals("beat-konongo-drill", view.id());
    assertEquals(3, view.licenseOptions().size());
    assertEquals("LEASE", view.licenseOptions().get(0).tier());
    assertEquals("PREMIUM", view.licenseOptions().get(1).tier());
    assertEquals("EXCLUSIVE", view.licenseOptions().get(2).tier());
  }

  @Test
  void get_unknownId_throwsStoreItemNotFoundException() {
    GetStoreItemService service = new GetStoreItemService(new FakeStoreRepository());

    assertThrows(
        StoreItemNotFoundException.class, () -> service.get(new StoreItemId("does-not-exist")));
  }
}
