package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.audit.fakes.FakeAuditWriter;
import org.shakvilla.beatzmedia.catalog.application.port.in.StudioReleaseView;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.SplitEntryCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.SubmitReleaseCommand;
import org.shakvilla.beatzmedia.catalog.application.port.in.SubmitRelease.UploadedTrackRef;
import org.shakvilla.beatzmedia.catalog.application.service.SubmitReleaseService;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitOver100Exception;
import org.shakvilla.beatzmedia.catalog.domain.TrackCountInvalidException;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;
import org.shakvilla.beatzmedia.platform.fakes.FakeClock;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;
import org.shakvilla.beatzmedia.platform.fakes.FakePlatformSettingsProvider;

/**
 * Unit tests for {@link SubmitReleaseService}. Covers LLFR-CATALOG-02.2 acceptance criteria.
 * No framework; plain JUnit 5.
 */
@Tag("unit")
class SubmitReleaseServiceTest {

  private FakeCatalogRepository repo;
  private FakePlatformSettingsProvider settingsProvider;
  private SubmitReleaseService service;

  private static final ArtistId ARTIST = new ArtistId("artist-1");

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    settingsProvider = new FakePlatformSettingsProvider(); // defaults: 24% bundle discount
    service = new SubmitReleaseService(
        repo, settingsProvider, FakeIds.sequential("rel"), FakeClock.fixed(), new FakeAuditWriter());
  }

  /** LLFR-CATALOG-02.2: single type with 2 tracks → 422 TRACK_COUNT_INVALID. */
  @Test
  void single_with_two_tracks_throws_TrackCountInvalid() {
    SubmitReleaseCommand cmd = cmd(
        "idem-1",
        ReleaseType.single,
        List.of(
            track("t1", 1, 1000, List.of()),
            track("t2", 2, 1000, List.of())));

    assertThrows(TrackCountInvalidException.class, () -> service.submit(cmd));
  }

  /** LLFR-CATALOG-02.2: splits summing > 100 → 422 SPLIT_OVER_100. */
  @Test
  void splits_over_100_throws_SplitOver100() {
    SubmitReleaseCommand cmd = cmd(
        "idem-2",
        ReleaseType.single,
        List.of(track("t1", 1, 1000,
            List.of(split(60), split(50))))); // 110 > 100

    assertThrows(SplitOver100Exception.class, () -> service.submit(cmd));
  }

  /** LLFR-CATALOG-02.2: ep with 3 tracks; list price = roundHalfUp(Σ × 0.76). */
  @Test
  void multi_track_ep_computes_bundle_price() {
    // 3 tracks at 1000 pesewas each => sum=3000; 3000 × 76/100 = 2280
    SubmitReleaseCommand cmd = cmd(
        "idem-3",
        ReleaseType.ep,
        List.of(
            track("t1", 1, 1000, List.of()),
            track("t2", 2, 1000, List.of()),
            track("t3", 3, 1000, List.of())));

    StudioReleaseView view = service.submit(cmd);

    assertNotNull(view);
    assertEquals(ReleaseStatus.in_review, view.status());
    // list_price_minor = 2280 pesewas → 22.80 GHS
    assertEquals(0, view.price().amount().compareTo(new java.math.BigDecimal("22.80")));
  }

  /** LLFR-CATALOG-02.2: single type — list price equals the one track's price (no discount). */
  @Test
  void single_type_no_discount_applied() {
    SubmitReleaseCommand cmd = cmd(
        "idem-4",
        ReleaseType.single,
        List.of(track("t1", 1, 500, List.of())));

    StudioReleaseView view = service.submit(cmd);

    // 500 pesewas = 5.00 GHS; no discount for single
    assertEquals(0, view.price().amount().compareTo(new java.math.BigDecimal("5.00")));
  }

  /** LLFR-CATALOG-02.2: same idempotency key returns cached result without re-processing. */
  @Test
  void same_idempotency_key_returns_same_release() {
    SubmitReleaseCommand cmd = cmd(
        "idem-same",
        ReleaseType.single,
        List.of(track("t1", 1, 500, List.of())));

    StudioReleaseView first = service.submit(cmd);
    StudioReleaseView second = service.submit(cmd); // same key

    assertEquals(first.id(), second.id());
    // Only one release stored
    assertEquals(1, repo.releasesByArtist(
        ARTIST,
        java.util.Optional.empty(),
        new org.shakvilla.beatzmedia.platform.domain.PageRequest(0, 100)).total());
  }

  // ---- helpers ----

  private SubmitReleaseCommand cmd(
      String idempotencyKey, ReleaseType type, List<UploadedTrackRef> tracks) {
    return new SubmitReleaseCommand(
        idempotencyKey, ARTIST, "Test Release", type, Visibility.PUBLIC, null, tracks);
  }

  private UploadedTrackRef track(String id, int pos, long price, List<SplitEntryCommand> splits) {
    return new UploadedTrackRef(id, pos, price, splits);
  }

  private SplitEntryCommand split(int percent) {
    return new SplitEntryCommand("Alice", "alice@example.com", "producer", percent, "self");
  }
}
