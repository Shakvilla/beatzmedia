package org.shakvilla.beatzmedia.catalog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReleaseSplitsTest {

  private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

  private SplitEntry split(String trackId, int percent) {
    return new SplitEntry("s-" + trackId + "-" + percent, trackId, "Producer",
        "prod@example.com", "Producer", percent, SplitConfirmation.pending);
  }

  private Release draftWithTrackAndSplits(List<SplitEntry> splits) {
    return Release.reconstitute(
        "r1", "artist-1", "Title", ReleaseType.single, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L)), null, null, splits);
  }

  @Test
  void reconstitute_carriesSplits_andDefaultOverloadIsEmpty() {
    Release withSplits = draftWithTrackAndSplits(List.of(split("t1", 30)));
    assertEquals(1, withSplits.getSplits().size());

    Release legacy = Release.reconstitute(
        "r2", "artist-1", "T", ReleaseType.single, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L)), null, null);
    assertTrue(legacy.getSplits().isEmpty());
  }

  @Test
  void submit_allowsPerTrackSumUnderOrEqual100() {
    // 40 + 60 = 100 on t1 (creator remainder 0) — valid (<= 100).
    Release r = draftWithTrackAndSplits(List.of(split("t1", 40), split("t1", 60)));
    r.submit(24, NOW); // no throw
    assertEquals(ReleaseStatus.in_review, r.getStatus());
  }

  @Test
  void submit_allowsSumBelow100_creatorHoldsRemainder() {
    Release r = draftWithTrackAndSplits(List.of(split("t1", 30))); // creator implicit 70
    r.submit(24, NOW);
    assertEquals(ReleaseStatus.in_review, r.getStatus());
  }

  @Test
  void submit_rejectsPerTrackSumOver100() {
    Release r = draftWithTrackAndSplits(List.of(split("t1", 60), split("t1", 60))); // 120
    assertThrows(SplitOver100Exception.class, () -> r.submit(24, NOW));
  }

  @Test
  void submit_validatesEachTrackIndependently() {
    Release r = Release.reconstitute(
        "r3", "artist-1", "Album", ReleaseType.ep, ReleaseStatus.draft,
        Visibility.PUBLIC, null, null, 0L, NOW, NOW,
        List.of(new ReleaseTrack("t1", 0, 250L), new ReleaseTrack("t2", 1, 250L),
            new ReleaseTrack("t3", 2, 250L)),
        null, null,
        List.of(split("t1", 50), split("t2", 200))); // t1 ok, t2 over
    assertThrows(SplitOver100Exception.class, () -> r.submit(24, NOW));
  }
}
