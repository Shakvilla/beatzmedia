package org.shakvilla.beatzmedia.catalog.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.application.port.out.CatalogRepository;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;
import org.shakvilla.beatzmedia.catalog.domain.OwnershipStatus;
import org.shakvilla.beatzmedia.catalog.domain.Release;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseId;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseTrack;
import org.shakvilla.beatzmedia.catalog.domain.ReleaseType;
import org.shakvilla.beatzmedia.catalog.domain.SplitConfirmation;
import org.shakvilla.beatzmedia.catalog.domain.SplitEntry;
import org.shakvilla.beatzmedia.catalog.domain.Track;
import org.shakvilla.beatzmedia.catalog.domain.TrackId;
import org.shakvilla.beatzmedia.catalog.domain.Visibility;

import io.quarkus.test.junit.QuarkusTest;

/**
 * WU-CAT-6: the split_entry write path. Persists a draft with one track + collaborator splits,
 * re-reads via findRelease, and asserts the replace-set semantics.
 */
@QuarkusTest
@Tag("it")
class SplitPersistenceIT {

  @Inject CatalogRepository repo;

  private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

  // Reuses the dev-seeded 'black-sherif' artist_profile row for the release.artist_id FK (same
  // pattern as CatalogEnumerationIT) — this test does not exercise artist provisioning.
  private static final String ARTIST_ID = "black-sherif";

  private SplitEntry split(String id, String trackId, int percent) {
    return new SplitEntry(id, trackId, "Producer", "prod@example.com", "Producer",
        percent, SplitConfirmation.pending);
  }

  @Transactional
  void seedReleaseWithTrack(String releaseId, String trackId) {
    // A real track row must exist (split_entry.track_id FK -> track(id)). Track has no static
    // stub factory, so build one directly (mirrors UploadReleaseTrackService's stub-track shape).
    repo.saveTrack(new Track(
        new TrackId(trackId),
        trackId + " title",
        new ArtistId(ARTIST_ID),
        null,
        null,
        null,
        200,
        "/images/placeholder.jpg",
        OwnershipStatus.free,
        null,
        0L,
        null,
        null,
        null,
        null,
        "ready"));
    Release r = Release.reconstitute(
        releaseId, ARTIST_ID, "T", ReleaseType.single, org.shakvilla.beatzmedia.catalog.domain.ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack(trackId, 0, 250L)), null, null, List.of());
    repo.saveRelease(r);
  }

  @Test
  @Transactional
  void saveTrackSplits_persists_and_findRelease_readsBack() {
    seedReleaseWithTrack("rel-sp-1", "trk-sp-1");

    repo.saveTrackSplits("trk-sp-1", List.of(split("sp-a", "trk-sp-1", 30), split("sp-b", "trk-sp-1", 20)));

    Release read = repo.findRelease(new ReleaseId("rel-sp-1")).orElseThrow();
    assertEquals(2, read.getSplits().size());
    assertEquals(50, read.getSplits().stream().mapToInt(SplitEntry::percent).sum());
    assertTrue(read.getSplits().stream().allMatch(s -> s.confirmation() == SplitConfirmation.pending));
  }

  @Test
  @Transactional
  void saveTrackSplits_replacesWholesale_andEmptyClears() {
    seedReleaseWithTrack("rel-sp-2", "trk-sp-2");

    repo.saveTrackSplits("trk-sp-2", List.of(split("x", "trk-sp-2", 40)));
    repo.saveTrackSplits("trk-sp-2", List.of(split("y", "trk-sp-2", 10))); // replace
    Release afterReplace = repo.findRelease(new ReleaseId("rel-sp-2")).orElseThrow();
    assertEquals(1, afterReplace.getSplits().size());
    assertEquals(10, afterReplace.getSplits().get(0).percent());

    repo.saveTrackSplits("trk-sp-2", List.of()); // clear
    Release afterClear = repo.findRelease(new ReleaseId("rel-sp-2")).orElseThrow();
    assertTrue(afterClear.getSplits().isEmpty());
  }

  @Test
  @Transactional
  void findReleaseByIdempotencyKey_loadsSplits() {
    seedReleaseWithTrack("rel-sp-3", "trk-sp-3");
    repo.saveTrackSplits("trk-sp-3", List.of(split("sp-c", "trk-sp-3", 15)));

    Release release = repo.findRelease(new ReleaseId("rel-sp-3")).orElseThrow();
    repo.saveReleaseWithIdempotencyKey(release, "idem-key-sp-3");

    Release replay = repo.findReleaseByIdempotencyKey("idem-key-sp-3").orElseThrow();
    assertEquals(1, replay.getSplits().size());
    assertEquals(15, replay.getSplits().get(0).percent());
    assertEquals("sp-c", replay.getSplits().get(0).id());
  }
}
