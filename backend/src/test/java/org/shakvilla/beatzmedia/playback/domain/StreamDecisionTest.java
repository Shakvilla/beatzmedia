package org.shakvilla.beatzmedia.playback.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link StreamDecision} INV-3 gate — the single point deciding FULL vs
 * PREVIEW. Playback ADD §3.
 */
@Tag("unit")
class StreamDecisionTest {

  @Test
  void forSale_notOwned_decidesPreview() {
    assertEquals(
        PlaybackMode.PREVIEW, StreamDecision.decide(TrackOwnership.FOR_SALE, false));
  }

  @Test
  void forSale_owned_decidesFull() {
    assertEquals(PlaybackMode.FULL, StreamDecision.decide(TrackOwnership.FOR_SALE, true));
  }

  @Test
  void free_notOwned_decidesFull() {
    assertEquals(PlaybackMode.FULL, StreamDecision.decide(TrackOwnership.FREE, false));
  }

  @Test
  void free_owned_decidesFull() {
    // "owned" is meaningless for a free track, but the gate is never more restrictive than FULL.
    assertEquals(PlaybackMode.FULL, StreamDecision.decide(TrackOwnership.FREE, true));
  }
}
