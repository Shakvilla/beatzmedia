package org.shakvilla.beatzmedia.engagement.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ListeningCheckpointTest {

  @Test
  void completionIsExplicitOrAtLeastNinetyFivePercent() {
    assertFalse(checkpoint(94, 100, EngagementEventType.PROGRESS).completed());
    assertTrue(checkpoint(95, 100, EngagementEventType.PROGRESS).completed());
    assertTrue(checkpoint(10, 100, EngagementEventType.COMPLETION).completed());
  }

  private static ListeningCheckpoint checkpoint(
      int position, int duration, EngagementEventType eventType) {
    return new ListeningCheckpoint(
        "checkpoint",
        "session",
        "account",
        1,
        ContentKind.PODCAST_EPISODE,
        "episode",
        eventType,
        position,
        duration,
        5,
        true,
        Instant.EPOCH);
  }
}
