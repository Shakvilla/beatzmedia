package org.shakvilla.beatzmedia.analytics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.catalog.domain.ArtistId;

/** Unit tests for {@link AudienceRollup} fold semantics. */
@Tag("unit")
class AudienceRollupTest {

  private static final ArtistId ARTIST = new ArtistId("artist-1");
  private static final RollupBucket BUCKET = RollupBucket.of(LocalDate.parse("2026-07-01"), Grain.DAILY);

  @Test
  void plusPlay_incrementsPlaysOnly() {
    AudienceRollup rollup = AudienceRollup.zero(ARTIST, BUCKET).plusPlay().plusPlay().plusPlay();
    assertEquals(3L, rollup.plays());
    assertEquals(0, rollup.followersGained());
  }

  @Test
  void plusFollower_incrementsFollowersGainedOnly() {
    AudienceRollup rollup = AudienceRollup.zero(ARTIST, BUCKET).plusFollower().plusFollower();
    assertEquals(2, rollup.followersGained());
    assertEquals(0L, rollup.plays());
  }

  @Test
  void withUniqueListeners_replacesCountWithoutTouchingOtherFields() {
    AudienceRollup rollup = AudienceRollup.zero(ARTIST, BUCKET).plusPlay().withUniqueListeners(7);
    assertEquals(7, rollup.uniqueListeners());
    assertEquals(1L, rollup.plays());
  }
}
