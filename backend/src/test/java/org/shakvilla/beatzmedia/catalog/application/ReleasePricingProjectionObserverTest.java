package org.shakvilla.beatzmedia.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.adapter.in.events.ReleasePricingProjectionObserver;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseWentLive;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;
import org.shakvilla.beatzmedia.catalog.fakes.FakeCatalogRepository;

/**
 * The artist's price is written to {@code release_track.price_minor}, but everything that decides
 * whether a track can be SOLD reads {@code track.ownership} / {@code track.price_minor} — both the
 * commerce pricing adapter and the fan-facing track view. Nothing bridged them.
 *
 * <p>The consequence was total: {@code OwnershipStatus.for_sale} was never assigned anywhere in
 * production code, so every uploaded track stayed on its upload-time stub ({@code free},
 * {@code null}) forever. Add-to-cart answered {@code 404 Price unavailable} for everything, and
 * fans saw every track as free no matter what the artist charged — on a buy-to-own platform.
 *
 * <p>It stayed invisible because the commerce tests seed {@code for-sale} straight into the track
 * table, so they assert a state production could never produce.
 *
 * <p>This projection closes it at the moment a release becomes purchasable, following the same
 * {@code ReleaseWentLive} / {@code AFTER_SUCCESS} shape the album and search projections already
 * use.
 */
@Tag("unit")
class ReleasePricingProjectionObserverTest {

  private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
  private static final String RELEASE = "rel-1";
  private static final String ARTIST = "art-1";

  private FakeCatalogRepository repo;
  private ReleasePricingProjectionObserver observer;

  @BeforeEach
  void setUp() {
    repo = new FakeCatalogRepository();
    observer = new ReleasePricingProjectionObserver(repo);
  }

  /** The row catalog writes at upload time: free, unpriced — the state that never changed. */
  private Track uploadedStub(String id) {
    return new Track(
        new TrackId(id), "Sunset Groove", new ArtistId(ARTIST), null, null, null,
        180, "/images/placeholder.jpg", OwnershipStatus.free, null, 0L, null, null, null, null,
        "ready");
  }

  /** Seed the tracks plus the live release that prices them. */
  private void seed(List<ReleaseTrack> tracks) {
    tracks.forEach(rt -> repo.saveTrack(uploadedStub(rt.trackId())));
    Release release = Release.create(
        RELEASE, ARTIST, "Sunset EP", ReleaseType.single, Visibility.PUBLIC, null,
        tracks, 24, NOW);
    release.setDownloadable(true);
    repo.addRelease(release);
  }

  private void seed(String trackId, long priceMinor) {
    seed(List.of(new ReleaseTrack(trackId, 1, priceMinor)));
  }

  @Test
  void aPricedTrackBecomesForSaleWhenItsReleaseGoesLive() {
    seed("t1", 250);

    observer.onReleaseWentLive(new ReleaseWentLive(RELEASE, ARTIST, NOW));

    Track t = repo.findTrack(new TrackId("t1")).orElseThrow();
    assertEquals(OwnershipStatus.for_sale, t.getOwnership(),
        "without this the track is unpurchasable and shows to fans as free");
    assertEquals(250L, t.getPriceMinor().orElseThrow());
  }

  /**
   * A zero price means free, not "for sale at nothing". The one track in the dev database has
   * price 0 and correctly reads free — that behaviour must survive this change, or the fix would
   * start charging for something the artist gave away.
   */
  @Test
  void aZeroPricedTrackStaysFree() {
    seed("t2", 0);

    observer.onReleaseWentLive(new ReleaseWentLive(RELEASE, ARTIST, NOW));

    Track t = repo.findTrack(new TrackId("t2")).orElseThrow();
    assertEquals(OwnershipStatus.free, t.getOwnership());
    assertTrue(t.getPriceMinor().isEmpty(), "a free track carries no price");
  }

  @Test
  void everyTrackOnTheReleaseIsProjectedNotJustTheFirst() {
    seed(List.of(new ReleaseTrack("t1", 1, 250), new ReleaseTrack("t2", 2, 400)));

    observer.onReleaseWentLive(new ReleaseWentLive(RELEASE, ARTIST, NOW));

    assertEquals(250L, repo.findTrack(new TrackId("t1")).orElseThrow().getPriceMinor().orElseThrow());
    assertEquals(400L, repo.findTrack(new TrackId("t2")).orElseThrow().getPriceMinor().orElseThrow());
  }

  /**
   * Re-firing the event (a reinstate re-publishes) must not cost a write. Same idempotence rule
   * {@code MediaReadyObserver} follows.
   */
  @Test
  void reProjectingAnUnchangedTrackCostsNoWrite() {
    seed("t1", 250);
    observer.onReleaseWentLive(new ReleaseWentLive(RELEASE, ARTIST, NOW));
    Track first = repo.findTrack(new TrackId("t1")).orElseThrow();

    observer.onReleaseWentLive(new ReleaseWentLive(RELEASE, ARTIST, NOW));

    assertSame(first, repo.findTrack(new TrackId("t1")).orElseThrow(),
        "an unchanged projection must not rewrite the row");
  }

  @Test
  void anUnknownReleaseIsIgnoredRatherThanThrowing() {
    observer.onReleaseWentLive(new ReleaseWentLive("no-such-release", ARTIST, NOW));

    assertNull(repo.findTrack(new TrackId("t1")).orElse(null));
  }
}
