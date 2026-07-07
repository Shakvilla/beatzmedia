package org.shakvilla.beatzmedia.analytics.adapter.in.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.shakvilla.beatzmedia.analytics.domain.FollowFact;
import org.shakvilla.beatzmedia.analytics.fakes.InMemoryFollowFactRepository;
import org.shakvilla.beatzmedia.library.domain.FollowKind;
import org.shakvilla.beatzmedia.library.domain.Followed;
import org.shakvilla.beatzmedia.platform.fakes.FakeIds;

/**
 * Unit tests for {@link FollowedObserver} — proves only {@code kind=artist} follows feed the
 * audience rollup; playlist/show follows are ignored.
 */
@Tag("unit")
class FollowedObserverTest {

  InMemoryFollowFactRepository followFacts;
  FollowedObserver observer;

  @BeforeEach
  void setUp() {
    followFacts = new InMemoryFollowFactRepository();
    observer = new FollowedObserver(followFacts, FakeIds.sequential("fact"));
  }

  @Test
  void onFollowed_artistKind_stagesFact() {
    Instant at = Instant.parse("2026-07-05T10:00:00Z");
    observer.onFollowed(new Followed("acc-1", FollowKind.artist, "artist-1", at));

    assertEquals(1, followFacts.findUnprocessed().size());
    FollowFact fact = followFacts.findUnprocessed().get(0);
    assertEquals("artist-1", fact.artistId());
    assertEquals(at, fact.occurredAt());
  }

  @Test
  void onFollowed_playlistKind_isIgnored() {
    observer.onFollowed(
        new Followed("acc-1", FollowKind.playlist, "pl-1", Instant.parse("2026-07-05T10:00:00Z")));
    assertTrue(followFacts.findUnprocessed().isEmpty());
  }

  @Test
  void onFollowed_showKind_isIgnored() {
    observer.onFollowed(
        new Followed("acc-1", FollowKind.show, "show-1", Instant.parse("2026-07-05T10:00:00Z")));
    assertTrue(followFacts.findUnprocessed().isEmpty());
  }
}
